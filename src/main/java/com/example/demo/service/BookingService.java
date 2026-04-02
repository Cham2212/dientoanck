package com.example.demo.service;

import com.example.demo.model.Booking;
import com.example.demo.repository.BookingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

@Service
public class BookingService {

    @Autowired
    private BookingRepository repository;

    private List<String> logs = new ArrayList<>();
    private String[] otherServers = {
            "https://dien-toan-lan-hai.onrender.com",
            "https://dientoanck.onrender.com",
            "https://demo2-75m2.onrender.com"
    };

    private ConcurrentHashMap<String, Boolean> serverStatus = new ConcurrentHashMap<>();
    private int clock = 0;

    public BookingService() {
        for (String url : otherServers) {
            serverStatus.put(url, true);
        }
    }

    private synchronized int tick() { return ++clock; }
    private synchronized void updateClock(int received) { clock = Math.max(clock, received) + 1; }

    private String log(String type, String message) {
        String time = LocalTime.now().withNano(0).toString();
        return "[" + time + "] [L=" + clock + "] [" + type + "] " + message;
    }

    public void book(Booking b, String serverId) {
        tick();
        logs.add(log("CLIENT", "Nhận request đặt phòng: " + b.getName()));
        b.setLamportTime(clock);

        CompletableFuture.runAsync(() -> {
            RestTemplate restTemplate = createRestTemplate();
            List<String> okServers = new ArrayList<>();

            // ===== PHA 1: PREPARE (VOTING) =====
            for (String url : otherServers) {
                boolean success = false;
                int retryCount = 0;
                int maxRetries = 2;

                while (!success && retryCount <= maxRetries) {
                    try {
                        tick();
                        if(retryCount > 0) logs.add(log("4PC", "Thử lại lần " + retryCount + " tới " + url));
                        else logs.add(log("4PC", "Gửi PREPARE tới " + url));

                        Boolean res = restTemplate.postForObject(url + "/api/prepare", b, Boolean.class);

                        if (Boolean.TRUE.equals(res)) {
                            okServers.add(url);
                            serverStatus.put(url, true);
                            logs.add(log("4PC", "VOTE OK từ " + url));
                            success = true;
                        } else {
                            logs.add(log("4PC", "VOTE FAIL từ " + url));
                            success = true; 
                        }
                    } catch (Exception e) {
                        retryCount++;
                        if (retryCount > maxRetries) {
                            serverStatus.put(url, false);
                            logs.add(log("ERROR", "Server DOWN sau " + maxRetries + " lần thử: " + url));
                            success = true; 
                        }
                    }
                }
            }

            // ===== QUYẾT ĐỊNH THEO QUORUM =====
            int threshold = (otherServers.length / 2); 

            if (okServers.size() >= threshold) {
                tick();
                logs.add(log("4PC", "QUORUM ĐẠT (" + okServers.size() + "/" + otherServers.length + ") → TIẾN TỚI PRE-COMMIT"));

                // ===== PHA 2: PRE-COMMIT (Tạm lưu/Chuẩn bị ghi) =====
                List<String> preAckServers = new ArrayList<>();
                for (String url : okServers) {
                    try {
                        tick();
                        logs.add(log("4PC", "Gửi lệnh PRE-COMMIT tới " + url));
                        // Gọi endpoint mới /api/pre-commit
                        String res = restTemplate.postForObject(url + "/api/pre-commit", b, String.class);
                        if ("PRE_ACK".equals(res)) {
                            preAckServers.add(url);
                            logs.add(log("4PC", "Nhận PRE_ACK từ " + url));
                        }
                    } catch (Exception e) {
                        logs.add(log("ERROR", "Lỗi gửi PRE-COMMIT tới " + url));
                    }
                }

                // Đảm bảo đa số đã nhận được lệnh lưu tạm mới sang Pha 3
                if (preAckServers.size() >= threshold) {
                    
                    // ===== PHA 3: COMMIT CHÍNH THỨC =====
                    tick();
                    logs.add(log("4PC", "ĐỦ XÁC NHẬN TẠM LƯU → GỬI COMMIT CUỐI CÙNG"));

                    // Commit local
                    repository.save(b);
                    logs.add(log("DATABASE", "Đã COMMIT local"));

                    // Gửi Commit tới các server
                    for (String url : preAckServers) {
                        try {
                            restTemplate.postForObject(url + "/api/commit", b, String.class);
                            // ===== PHA 4: ACK (Xác nhận hoàn tất) =====
                            logs.add(log("4PC", "Nhận ACK hoàn tất từ " + url));
                        } catch (Exception e) {
                            logs.add(log("ERROR", "Không thể gửi COMMIT tới " + url));
                        }
                    }
                } else {
                    logs.add(log("4PC", "ABORT: Không đủ xác nhận PRE-COMMIT"));
                    sendAbort(restTemplate, okServers, b);
                }

            } else {
                tick();
                logs.add(log("4PC", "ABORT: Quorum fail (Không đủ server OK)"));
                sendAbort(restTemplate, okServers, b);
            }
        });
    }

    private void sendAbort(RestTemplate rt, List<String> servers, Booking b) {
        for (String url : servers) {
            try { rt.postForObject(url + "/api/abort", b, String.class); } catch (Exception e) {}
        }
    }

    // --- CÁC ENDPOINT CHO SERVER THÀNH VIÊN ---

    public boolean prepare(Booking b) {
        updateClock(b.getLamportTime());
        logs.add(log("4PC", "Nhận PREPARE (Pha 1)"));
        return true;
    }

    // API MỚI CHO 4PC
    public String preCommit(Booking b) {
        updateClock(b.getLamportTime());
        logs.add(log("4PC", "Nhận PRE-COMMIT (Pha 2): Đã lưu tạm thời vào Log"));
        // Ở đây bạn có thể lưu b vào một bảng tạm hoặc File log để phục hồi sau này
        return "PRE_ACK";
    }

    public void commit(Booking b) {
        updateClock(b.getLamportTime());
        logs.add(log("4PC", "Nhận COMMIT (Pha 3): Ghi vĩnh viễn vào DB"));
        repository.save(b);
        tick();
        logs.add(log("DATABASE", "Đã COMMIT thành công"));
    }

    private RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(3000);
        return new RestTemplate(factory);
    }

    public List<String> getLogs() { return logs; }
    public ConcurrentHashMap<String, Boolean> getServerStatus() { return serverStatus; }
}
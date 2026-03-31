package com.example.demo.service;

import com.example.demo.model.Booking;
import com.example.demo.repository.BookingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate; // Đảm bảo đã import cái này

import java.util.ArrayList;
import java.util.List;

@Service
public class BookingService {

    @Autowired
    private BookingRepository repository;

    private List<String> logs = new ArrayList<>();

    // Danh sách server khác
    private String[] otherServers = {
            "https://server2.onrender.com",
            "https://server3.onrender.com"
    };

    public void book(Booking b, String serverId) {
        // 1. Lưu DB của mình
        repository.save(b);

        // 2. Log
        logs.add("Server " + serverId + " nhận booking: " + b.getName());

        // 3. Gửi sang server khác (Chỉ gửi nếu chưa được replicate)
        // Đây là khúc sửa theo ảnh "Sửa book()"
        if (!b.isReplicated()) {
            b.setReplicated(true); // Đánh dấu đã replicate để không lặp lại

            RestTemplate restTemplate = new RestTemplate();
            for (String url : otherServers) {
                try {
                    restTemplate.postForObject(
                            url + "/api/replicate",
                            b,
                            String.class);
                } catch (Exception e) {
                    System.out.println("Không gửi được tới " + url + ": " + e.getMessage());
                }
            }
        }
    }

    public void replicate(Booking b, String serverId) {
        // Đây là khúc sửa theo ảnh "Sửa replicate()"
        b.setReplicated(true);
        repository.save(b);

        logs.add("Server " + serverId + " nhận từ server khác: " + b.getName());
    }

    public List<String> getLogs() {
        return logs;
    }
}
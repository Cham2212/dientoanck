package com.example.demo.service;

import com.example.demo.model.Booking;
import com.example.demo.repository.BookingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class BookingService {

    @Autowired
    private BookingRepository repo;

    private List<String> logs = new ArrayList<>();

    public void book(Booking b, String serverId) {

        logs.add(now() + " - SERVER " + serverId + " - NHẬN REQUEST");

        try {
            repo.save(b);

            logs.add(now() + " - SERVER " + serverId + " - ĐẶT PHÒNG THÀNH CÔNG");

        } catch (Exception e) {
            logs.add(now() + " - SERVER " + serverId + " - LỖI");
        }
    }

    public List<String> getLogs() {
        return logs;
    }

    private String now() {
        return java.time.LocalTime.now().toString();
    }

}
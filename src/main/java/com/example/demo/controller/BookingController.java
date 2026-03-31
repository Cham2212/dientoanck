package com.example.demo.controller;

import com.example.demo.model.Booking;
import com.example.demo.service.BookingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin
public class BookingController {

    @Autowired
    private BookingService service;

    @Value("${server.id}")
    private String serverId;

    @PostMapping("/book")
    public String book(@RequestBody Booking b) {
        service.book(b, serverId);
        return "Booked from SERVER " + serverId;
    }

    @GetMapping("/log")
    public List<String> logs() {
        return service.getLogs();
    }
    
}
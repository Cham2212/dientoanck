package com.example.demo.model;

import jakarta.persistence.*;

@Entity
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String room;
    private String checkin;
    private String checkout;
    private boolean replicated;

    // getter setter
}
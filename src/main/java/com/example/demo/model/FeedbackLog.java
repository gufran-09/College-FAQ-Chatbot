package com.example.demo.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class FeedbackLog {
    @Id
    private int feedbackId;
}

package com.sp.demo.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

@Service
public class DummyGmailService implements GmailService{


  @Override
  public String sendEmail(String threadId, String body) {
    return UUID.randomUUID().toString();
  }
}

package com.example.demo.service;

import java.util.concurrent.Semaphore;

/**
 * Shared across WordService and PdfService — both spawn LibreOffice
 * (soffice) processes for conversion. On memory-constrained hosts
 * (e.g. Render free tier's 512MB), two soffice processes running
 * concurrently can exceed available RAM and get the container killed
 * (shows up as unexplained 502/503s with no Java stack trace).
 * This ensures only one LibreOffice conversion runs at a time.
 */
public class LibreOfficeLock {
    private static final Semaphore LOCK = new Semaphore(1);

    public static void acquire() throws InterruptedException {
        LOCK.acquire();
    }

    public static void release() {
        LOCK.release();
    }
}
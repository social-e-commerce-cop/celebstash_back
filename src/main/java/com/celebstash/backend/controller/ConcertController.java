package com.celebstash.backend.controller;

import com.celebstash.backend.dto.concert.*;
import com.celebstash.backend.service.ConcertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/concerts")
@RequiredArgsConstructor
@Tag(name = "Concerts & Meet-ups", description = "Concerts, meet-ups, and ticketing APIs")
public class ConcertController {

    private final ConcertService concertService;

    @PostMapping
    @Operation(summary = "Create concert event", description = "Allows verified creators to create a concert event with ticket capacity and pricing")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ConcertResponse> createConcert(@Valid @RequestBody ConcertRequest request) {
        return new ResponseEntity<>(concertService.createConcert(request), HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(summary = "List all concerts", description = "Retrieves all upcoming concerts and meet-up events")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<ConcertResponse>> getAllConcerts() {
        return ResponseEntity.ok(concertService.getAllConcerts());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get concert details", description = "Retrieves details of a specific concert event")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ConcertResponse> getConcertById(@PathVariable Long id) {
        return ResponseEntity.ok(concertService.getConcertById(id));
    }

    @PostMapping("/{id}/tickets")
    @Operation(summary = "Purchase concert ticket", description = "Allows user to purchase a ticket using wallet balance")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<TicketResponse> purchaseTicket(
            @PathVariable Long id,
            @Valid @RequestBody TicketPurchaseRequest request) {
        return ResponseEntity.ok(concertService.purchaseTicket(id, request));
    }

    @PostMapping("/tickets/verify")
    @Operation(summary = "Verify ticket check-in", description = "Allows staff or admin to scan verification code to check in attendee")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<TicketResponse> verifyTicket(@Valid @RequestBody TicketVerificationRequest request) {
        return ResponseEntity.ok(concertService.verifyTicket(request.getVerificationCode()));
    }

    @GetMapping("/tickets/my")
    @Operation(summary = "Get my purchased tickets", description = "Retrieves all tickets purchased by the authenticated user")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<TicketResponse>> getMyTickets() {
        return ResponseEntity.ok(concertService.getMyTickets());
    }
}

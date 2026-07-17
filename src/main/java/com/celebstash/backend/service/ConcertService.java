package com.celebstash.backend.service;

import com.celebstash.backend.dto.concert.*;
import com.celebstash.backend.exception.AppException;
import com.celebstash.backend.model.*;
import com.celebstash.backend.model.enums.*;
import com.celebstash.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConcertService {

    private final ConcertRepository concertRepository;
    private final TicketRepository ticketRepository;
    private final UserService userService;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final NotificationService notificationService;

    @Transactional
    public ConcertResponse createConcert(ConcertRequest request) {
        User creator = userService.getCurrentUser();

        if (creator.getRole() != Role.ARTIST || !creator.isAccountVerified()) {
            throw new AppException("Only verified creators are allowed to create concerts", HttpStatus.FORBIDDEN);
        }

        Concert concert = Concert.builder()
                .name(request.getName())
                .description(request.getDescription())
                .date(request.getDate())
                .venue(request.getVenue())
                .ticketPriceGeneral(request.getTicketPriceGeneral())
                .ticketPriceVip(request.getTicketPriceVip())
                .capacity(request.getCapacity())
                .availableTickets(request.getCapacity())
                .coverImage(request.getCoverImage())
                .seller(creator)
                .build();

        Concert saved = concertRepository.save(concert);
        return mapToConcertResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ConcertResponse> getAllConcerts() {
        return concertRepository.findAll().stream()
                .map(this::mapToConcertResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ConcertResponse getConcertById(Long id) {
        Concert concert = concertRepository.findById(id)
                .orElseThrow(() -> new AppException("Concert not found", HttpStatus.NOT_FOUND));
        return mapToConcertResponse(concert);
    }

    @Transactional
    public TicketResponse purchaseTicket(Long concertId, TicketPurchaseRequest request) {
        User buyer = userService.getCurrentUser();
        Concert concert = concertRepository.findById(concertId)
                .orElseThrow(() -> new AppException("Concert not found", HttpStatus.NOT_FOUND));

        if (concert.getAvailableTickets() <= 0) {
            throw new AppException("Event is sold out", HttpStatus.BAD_REQUEST);
        }

        // Determine price based on ticket tier
        BigDecimal price = request.getTicketTier() == TicketTier.VIP ? 
                concert.getTicketPriceVip() : concert.getTicketPriceGeneral();

        // Validate wallet and PIN
        Wallet buyerWallet = walletRepository.findByUser(buyer)
                .orElseThrow(() -> new AppException("Buyer wallet not found", HttpStatus.NOT_FOUND));

        if (buyerWallet.getPin() == null || !buyerWallet.getPin().equals(request.getPin())) {
            throw new AppException("Invalid PIN", HttpStatus.BAD_REQUEST);
        }

        if (buyerWallet.getBalance().compareTo(price) < 0) {
            throw new AppException("Insufficient balance to buy ticket", HttpStatus.BAD_REQUEST);
        }

        // Deduct price from buyer
        buyerWallet.setBalance(buyerWallet.getBalance().subtract(price));
        walletRepository.save(buyerWallet);

        // Credit artist minus 5% platform commission
        BigDecimal platformCommission = price.multiply(new BigDecimal("0.05"));
        BigDecimal artistPayout = price.subtract(platformCommission);

        User artist = concert.getSeller();
        Wallet artistWallet = walletRepository.findByUser(artist)
                .orElseGet(() -> {
                    Wallet newWallet = Wallet.builder()
                            .user(artist)
                            .balance(BigDecimal.ZERO)
                            .createdAt(LocalDateTime.now())
                            .build();
                    return walletRepository.save(newWallet);
                });

        artistWallet.setBalance(artistWallet.getBalance().add(artistPayout));
        walletRepository.save(artistWallet);

        // Decrement available tickets
        concert.setAvailableTickets(concert.getAvailableTickets() - 1);
        concertRepository.save(concert);

        // Generate verification code
        String verificationCode = "TKT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // Save ticket
        Ticket ticket = Ticket.builder()
                .concert(concert)
                .user(buyer)
                .ticketTier(request.getTicketTier())
                .verificationCode(verificationCode)
                .build();
        Ticket savedTicket = ticketRepository.save(ticket);

        // Record transactions
        Transaction buyerTx = Transaction.builder()
                .wallet(buyerWallet)
                .amount(price)
                .type(TransactionType.PAYMENT)
                .status(TransactionStatus.COMPLETED)
                .description("Ticket purchase (" + request.getTicketTier() + ") for " + concert.getName())
                .createdAt(LocalDateTime.now())
                .completedAt(LocalDateTime.now())
                .build();
        transactionRepository.save(buyerTx);

        Transaction artistTx = Transaction.builder()
                .wallet(artistWallet)
                .amount(artistPayout)
                .type(TransactionType.DEPOSIT)
                .status(TransactionStatus.COMPLETED)
                .description("Ticket sale payout for " + concert.getName() + " (minus 5% platform fee)")
                .createdAt(LocalDateTime.now())
                .completedAt(LocalDateTime.now())
                .build();
        transactionRepository.save(artistTx);

        // Notify buyer
        notificationService.createNotification(
                buyer,
                "Ticket Purchased",
                "You bought a ticket for '" + concert.getName() + "'. Code: " + verificationCode,
                NotificationType.ORDER_STATUS
        );

        // Notify artist
        notificationService.createNotification(
                artist,
                "Concert Ticket Sold",
                "A ticket has been sold for '" + concert.getName() + "'. Payout: $" + artistPayout + ".",
                NotificationType.ORDER_STATUS
        );

        return mapToTicketResponse(savedTicket);
    }

    @Transactional
    public TicketResponse verifyTicket(String verificationCode) {
        // Authenticate staff (must be admin or the concert artist/staff)
        User verifier = userService.getCurrentUser();

        Ticket ticket = ticketRepository.findByVerificationCode(verificationCode)
                .orElseThrow(() -> new AppException("Ticket not found", HttpStatus.NOT_FOUND));

        if (ticket.isUsed()) {
            throw new AppException("Ticket has already been verified and checked in", HttpStatus.BAD_REQUEST);
        }

        // Check if verifier is admin or the concert owner
        if (verifier.getRole() != Role.ADMIN && !ticket.getConcert().getSeller().getId().equals(verifier.getId())) {
            throw new AppException("Access denied: Only admins or event hosts can verify tickets", HttpStatus.FORBIDDEN);
        }

        // Mark ticket as used
        ticket.setUsed(true);
        Ticket updated = ticketRepository.save(ticket);

        // Notify attendee of check-in
        notificationService.createNotification(
                ticket.getUser(),
                "Checked In",
                "You have been successfully checked in for '" + ticket.getConcert().getName() + "'.",
                NotificationType.ORDER_STATUS
        );

        return mapToTicketResponse(updated);
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> getMyTickets() {
        User currentUser = userService.getCurrentUser();
        return ticketRepository.findByUser(currentUser).stream()
                .map(this::mapToTicketResponse)
                .collect(Collectors.toList());
    }

    private ConcertResponse mapToConcertResponse(Concert concert) {
        return ConcertResponse.builder()
                .id(concert.getId())
                .name(concert.getName())
                .description(concert.getDescription())
                .date(concert.getDate())
                .venue(concert.getVenue())
                .ticketPriceGeneral(concert.getTicketPriceGeneral())
                .ticketPriceVip(concert.getTicketPriceVip())
                .capacity(concert.getCapacity())
                .availableTickets(concert.getAvailableTickets())
                .coverImage(concert.getCoverImage())
                .sellerId(concert.getSeller().getId())
                .sellerName(concert.getSeller().getFullName())
                .createdAt(concert.getCreatedAt())
                .build();
    }

    private TicketResponse mapToTicketResponse(Ticket ticket) {
        return TicketResponse.builder()
                .id(ticket.getId())
                .concertId(ticket.getConcert().getId())
                .concertName(ticket.getConcert().getName())
                .concertDate(ticket.getConcert().getDate())
                .venue(ticket.getConcert().getVenue())
                .userId(ticket.getUser().getId())
                .userName(ticket.getUser().getFullName())
                .ticketTier(ticket.getTicketTier())
                .verificationCode(ticket.getVerificationCode())
                .isUsed(ticket.isUsed())
                .purchasedAt(ticket.getPurchasedAt())
                .build();
    }
}

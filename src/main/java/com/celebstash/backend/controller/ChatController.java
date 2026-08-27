package com.celebstash.backend.controller;

import com.celebstash.backend.dto.chat.*;
import com.celebstash.backend.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Tag(name = "Chat", description = "Chat conversations and messages APIs")
@SecurityRequirement(name = "bearerAuth")
public class ChatController {

    private final ChatService chatService;

    // ── Conversations ────────────────────────────────────────────────────────

    @GetMapping("/conversations")
    @Operation(summary = "Get all conversations for current user")
    public ResponseEntity<List<ConversationDto>> getConversations() {
        return ResponseEntity.ok(chatService.getMyConversations());
    }

    @PostMapping("/conversations")
    @Operation(summary = "Start a direct conversation with another user")
    public ResponseEntity<ConversationDto> startDirectConversation(
            @Valid @RequestBody CreateConversationRequest req) {
        return ResponseEntity.ok(chatService.startDirectConversation(req));
    }

    @PostMapping("/groups")
    @Operation(summary = "Create a group conversation")
    public ResponseEntity<ConversationDto> createGroup(
            @Valid @RequestBody CreateGroupRequest req) {
        return ResponseEntity.ok(chatService.createGroup(req));
    }

    @PutMapping("/conversations/{id}/group")
    @Operation(summary = "Update group name, description, or avatar")
    public ResponseEntity<ConversationDto> updateGroup(
            @PathVariable Long id,
            @RequestBody UpdateGroupRequest req) {
        return ResponseEntity.ok(chatService.updateGroup(id, req));
    }

    @PutMapping("/conversations/{id}/mute")
    @Operation(summary = "Toggle mute for a conversation")
    public ResponseEntity<Void> toggleMute(@PathVariable Long id) {
        chatService.toggleMute(id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/conversations/{id}/archive")
    @Operation(summary = "Toggle archive for a conversation")
    public ResponseEntity<Void> toggleArchive(@PathVariable Long id) {
        chatService.toggleArchive(id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/conversations/{id}/pin")
    @Operation(summary = "Toggle pin for a conversation")
    public ResponseEntity<Void> toggleConversationPin(@PathVariable Long id) {
        chatService.togglePin(id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/conversations/{id}/favorite")
    @Operation(summary = "Toggle favorite for a conversation")
    public ResponseEntity<Void> toggleFavorite(@PathVariable Long id) {
        chatService.toggleFavorite(id);
        return ResponseEntity.ok().build();
    }

    // ── Messages ─────────────────────────────────────────────────────────────

    @GetMapping("/conversations/{id}/messages")
    @Operation(summary = "Get paginated messages for a conversation")
    public ResponseEntity<Page<MessageDto>> getMessages(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "40") int size) {
        return ResponseEntity.ok(chatService.getMessages(id, page, size));
    }

    @PostMapping("/conversations/{id}/messages")
    @Operation(summary = "Send a message in a conversation")
    public ResponseEntity<MessageDto> sendMessage(
            @PathVariable Long id,
            @Valid @RequestBody SendMessageRequest req) {
        return ResponseEntity.ok(chatService.sendMessage(id, req));
    }

    @PutMapping("/messages/{id}")
    @Operation(summary = "Edit the text content of a message")
    public ResponseEntity<MessageDto> editMessage(
            @PathVariable Long id,
            @RequestBody EditMessageRequest req) {
        return ResponseEntity.ok(chatService.editMessage(id, req.content()));
    }

    @DeleteMapping("/messages/{id}")
    @Operation(summary = "Delete a message (for me or for everyone)")
    public ResponseEntity<Void> deleteMessage(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean forEveryone) {
        chatService.deleteMessage(id, forEveryone);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/messages/{id}/pin")
    @Operation(summary = "Toggle pin status on a message")
    public ResponseEntity<MessageDto> toggleMessagePin(@PathVariable Long id) {
        return ResponseEntity.ok(chatService.toggleMessagePin(id));
    }

    @PutMapping("/messages/{id}/star")
    @Operation(summary = "Toggle star status on a message")
    public ResponseEntity<MessageDto> toggleMessageStar(@PathVariable Long id) {
        return ResponseEntity.ok(chatService.toggleMessageStar(id));
    }

    @PostMapping("/messages/{id}/react")
    @Operation(summary = "Add or remove an emoji reaction from a message")
    public ResponseEntity<MessageDto> reactToMessage(
            @PathVariable Long id,
            @RequestParam String emoji) {
        return ResponseEntity.ok(chatService.reactToMessage(id, emoji));
    }

    // ── User search ──────────────────────────────────────────────────────────

    @GetMapping("/users/search")
    @Operation(summary = "Search users to start a new conversation")
    public ResponseEntity<List<UserSearchDto>> searchUsers(@RequestParam String q) {
        return ResponseEntity.ok(chatService.searchUsers(q));
    }
}

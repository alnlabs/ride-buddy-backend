package com.alnlabs.ridebuddy.chat;

import com.alnlabs.ridebuddy.booking.BookingEntity;
import com.alnlabs.ridebuddy.booking.BookingRepository;
import com.alnlabs.ridebuddy.common.ApiException;
import com.alnlabs.ridebuddy.profile.ProfileService;
import com.alnlabs.ridebuddy.request.RideOfferEntity;
import com.alnlabs.ridebuddy.request.RideOfferRepository;
import com.alnlabs.ridebuddy.request.RideRequestEntity;
import com.alnlabs.ridebuddy.request.RideRequestRepository;
import com.alnlabs.ridebuddy.ride.RideEntity;
import com.alnlabs.ridebuddy.ride.RideService;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ChatService {

    private final ChatConversationRepository conversationRepo;
    private final ChatMessageRepository messageRepo;
    private final BookingRepository bookingRepo;
    private final RideOfferRepository offerRepo;
    private final RideRequestRepository requestRepo;
    private final RideService rideService;
    private final ProfileService profileService;
    private final SimpMessagingTemplate messaging;

    public ChatService(
            ChatConversationRepository conversationRepo,
            ChatMessageRepository messageRepo,
            BookingRepository bookingRepo,
            RideOfferRepository offerRepo,
            RideRequestRepository requestRepo,
            RideService rideService,
            ProfileService profileService,
            SimpMessagingTemplate messaging
    ) {
        this.conversationRepo = conversationRepo;
        this.messageRepo = messageRepo;
        this.bookingRepo = bookingRepo;
        this.offerRepo = offerRepo;
        this.requestRepo = requestRepo;
        this.rideService = rideService;
        this.profileService = profileService;
        this.messaging = messaging;
    }

    @Transactional
    public ChatConversationEntity ensureForBooking(BookingEntity booking) {
        RideEntity ride = rideService.require(booking.getRideId());
        return upsert(
                ride.getId(),
                ride.getOwnerId(),
                booking.getPassengerId(),
                booking.getId(),
                null
        );
    }

    @Transactional
    public ChatConversationEntity ensureForOffer(RideOfferEntity offer, RideEntity ride, RideRequestEntity request) {
        return upsert(
                ride.getId(),
                ride.getOwnerId(),
                request.getRequesterId(),
                null,
                offer.getId()
        );
    }

    @Transactional
    public void attachBooking(UUID rideId, UUID coRiderId, UUID bookingId) {
        conversationRepo.findByRideIdAndCoRiderId(rideId, coRiderId).ifPresent(c -> {
            if (c.getBookingId() == null) {
                c.setBookingId(bookingId);
                conversationRepo.save(c);
            }
        });
    }

    private ChatConversationEntity upsert(
            UUID rideId,
            UUID hostId,
            UUID coRiderId,
            UUID bookingId,
            UUID offerId
    ) {
        ChatConversationEntity existing = conversationRepo.findByRideIdAndCoRiderId(rideId, coRiderId).orElse(null);
        if (existing != null) {
            if (bookingId != null && existing.getBookingId() == null) {
                existing.setBookingId(bookingId);
            }
            if (offerId != null && existing.getOfferId() == null) {
                existing.setOfferId(offerId);
            }
            return conversationRepo.save(existing);
        }
        ChatConversationEntity c = new ChatConversationEntity();
        c.setRideId(rideId);
        c.setHostId(hostId);
        c.setCoRiderId(coRiderId);
        c.setBookingId(bookingId);
        c.setOfferId(offerId);
        return conversationRepo.save(c);
    }

    public List<ConversationResponse> listMine(UUID userId) {
        return conversationRepo
                .findByHostIdOrCoRiderIdOrderByLastMessageAtDescCreatedAtDesc(userId, userId)
                .stream()
                .map(c -> toConversation(c, userId))
                .toList();
    }

    @Transactional
    public ConversationResponse open(UUID userId, OpenConversationRequest body) {
        ChatConversationEntity c;
        if (body.bookingId() != null) {
            BookingEntity booking = bookingRepo.findById(body.bookingId())
                    .orElseThrow(() -> ApiException.notFound("Booking not found"));
            RideEntity ride = rideService.require(booking.getRideId());
            if (!userId.equals(ride.getOwnerId()) && !userId.equals(booking.getPassengerId())) {
                throw ApiException.forbidden("Not part of this booking");
            }
            if (!canChatBooking(booking.getStatus()) && conversationRepo
                    .findByRideIdAndCoRiderId(ride.getId(), booking.getPassengerId()).isEmpty()) {
                throw ApiException.conflict("Chat is not available for this booking");
            }
            c = ensureForBooking(booking);
        } else if (body.offerId() != null) {
            RideOfferEntity offer = offerRepo.findById(body.offerId())
                    .orElseThrow(() -> ApiException.notFound("Offer not found"));
            RideRequestEntity req = requestRepo.findById(offer.getRequestId())
                    .orElseThrow(() -> ApiException.notFound("Ride request not found"));
            RideEntity ride = rideService.require(offer.getRideId());
            if (!userId.equals(ride.getOwnerId()) && !userId.equals(req.getRequesterId())) {
                throw ApiException.forbidden("Not part of this offer");
            }
            if (!canChatOffer(offer.getStatus()) && conversationRepo
                    .findByRideIdAndCoRiderId(ride.getId(), req.getRequesterId()).isEmpty()) {
                throw ApiException.conflict("Chat is not available for this offer");
            }
            c = ensureForOffer(offer, ride, req);
        } else if (body.rideId() != null && body.coRiderId() != null) {
            RideEntity ride = rideService.require(body.rideId());
            if (!userId.equals(ride.getOwnerId()) && !userId.equals(body.coRiderId())) {
                throw ApiException.forbidden("Not part of this ride chat");
            }
            c = conversationRepo.findByRideIdAndCoRiderId(body.rideId(), body.coRiderId())
                    .orElseThrow(() -> ApiException.notFound("Conversation not found — request a seat or send an offer first"));
            requireParticipant(c, userId);
        } else {
            throw ApiException.badRequest("Provide bookingId, offerId, or rideId+coRiderId");
        }
        return toConversation(c, userId);
    }

    @Transactional
    public List<MessageResponse> messages(UUID userId, UUID conversationId, Instant before, int limit) {
        ChatConversationEntity c = requireParticipantConversation(conversationId, userId);
        int pageSize = Math.min(Math.max(limit, 1), 100);
        List<ChatMessageEntity> page = messageRepo.findPage(
                conversationId,
                before,
                PageRequest.of(0, pageSize)
        );
        Instant now = Instant.now();
        if (userId.equals(c.getHostId())) {
            c.setHostLastReadAt(now);
        } else {
            c.setCoRiderLastReadAt(now);
        }
        conversationRepo.save(c);
        return page.stream().map(this::toMessage).toList();
    }

    @Transactional
    public MessageResponse send(UUID userId, UUID conversationId, String rawBody) {
        ChatConversationEntity c = requireParticipantConversation(conversationId, userId);
        if (!canSend(c)) {
            throw ApiException.conflict("Chat is closed for this ride");
        }
        String body = rawBody == null ? "" : rawBody.trim();
        if (body.isEmpty() || body.length() > 2000) {
            throw ApiException.badRequest("Message must be 1–2000 characters");
        }
        ChatMessageEntity m = new ChatMessageEntity();
        m.setConversationId(c.getId());
        m.setSenderId(userId);
        m.setBody(body);
        messageRepo.save(m);

        Instant at = m.getCreatedAt();
        c.setLastMessageAt(at);
        c.setLastMessagePreview(body.length() > 120 ? body.substring(0, 117) + "…" : body);
        if (userId.equals(c.getHostId())) {
            c.setHostLastReadAt(at);
        } else {
            c.setCoRiderLastReadAt(at);
        }
        conversationRepo.save(c);

        MessageResponse dto = toMessage(m);
        push(c.getHostId(), dto);
        push(c.getCoRiderId(), dto);
        return dto;
    }

    private void push(UUID userId, MessageResponse dto) {
        messaging.convertAndSendToUser(userId.toString(), "/queue/chat", dto);
    }

    private boolean canSend(ChatConversationEntity c) {
        if (c.getBookingId() != null) {
            BookingEntity b = bookingRepo.findById(c.getBookingId()).orElse(null);
            if (b != null && canChatBooking(b.getStatus())) return true;
        }
        if (c.getOfferId() != null) {
            RideOfferEntity o = offerRepo.findById(c.getOfferId()).orElse(null);
            if (o != null && canChatOffer(o.getStatus())) return true;
        }
        // Fallback: live booking/offer for this ride+co-rider
        BookingEntity booking = bookingRepo.findByRideIdAndPassengerId(c.getRideId(), c.getCoRiderId()).orElse(null);
        if (booking != null && canChatBooking(booking.getStatus())) return true;
        return offerRepo.findByOwnerIdOrderByCreatedAtDesc(c.getHostId()).stream()
                .anyMatch(o -> o.getRideId().equals(c.getRideId())
                        && canChatOffer(o.getStatus())
                        && requestRepo.findById(o.getRequestId())
                        .map(r -> r.getRequesterId().equals(c.getCoRiderId()))
                        .orElse(false));
    }

    private static boolean canChatBooking(String status) {
        return "requested".equals(status) || "accepted".equals(status);
    }

    private static boolean canChatOffer(String status) {
        return "offered".equals(status) || "accepted".equals(status);
    }

    private ChatConversationEntity requireParticipantConversation(UUID conversationId, UUID userId) {
        ChatConversationEntity c = conversationRepo.findById(conversationId)
                .orElseThrow(() -> ApiException.notFound("Conversation not found"));
        requireParticipant(c, userId);
        return c;
    }

    private void requireParticipant(ChatConversationEntity c, UUID userId) {
        if (!userId.equals(c.getHostId()) && !userId.equals(c.getCoRiderId())) {
            throw ApiException.forbidden("Not part of this conversation");
        }
    }

    private ConversationResponse toConversation(ChatConversationEntity c, UUID viewerId) {
        RideEntity ride = rideService.require(c.getRideId());
        boolean viewerIsHost = viewerId.equals(c.getHostId());
        UUID peerId = viewerIsHost ? c.getCoRiderId() : c.getHostId();
        Instant myRead = viewerIsHost ? c.getHostLastReadAt() : c.getCoRiderLastReadAt();
        Instant after = myRead != null ? myRead : Instant.EPOCH;
        long unread = messageRepo.countByConversationIdAndCreatedAtAfterAndSenderIdNot(
                c.getId(), after, viewerId
        );
        return new ConversationResponse(
                c.getId(),
                c.getRideId(),
                c.getHostId(),
                c.getCoRiderId(),
                c.getBookingId(),
                c.getOfferId(),
                ride.getOriginLabel(),
                ride.getDestinationLabel(),
                ride.getDepartAt(),
                viewerIsHost ? "host" : "co_rider",
                profileService.posterCard(peerId),
                c.getLastMessagePreview(),
                c.getLastMessageAt(),
                (int) Math.min(unread, Integer.MAX_VALUE),
                canSend(c)
        );
    }

    private MessageResponse toMessage(ChatMessageEntity m) {
        return new MessageResponse(
                m.getId(),
                m.getConversationId(),
                m.getSenderId(),
                m.getBody(),
                m.getCreatedAt()
        );
    }

    public record OpenConversationRequest(UUID bookingId, UUID offerId, UUID rideId, UUID coRiderId) {}

    public record SendMessageRequest(String body) {}

    public record MessageResponse(
            UUID id,
            UUID conversationId,
            UUID senderId,
            String body,
            Instant createdAt
    ) {}

    public record ConversationResponse(
            UUID id,
            UUID rideId,
            UUID hostId,
            UUID coRiderId,
            UUID bookingId,
            UUID offerId,
            String rideOriginLabel,
            String rideDestinationLabel,
            Instant departAt,
            String myRole,
            ProfileService.PosterCard peer,
            String lastMessagePreview,
            Instant lastMessageAt,
            int unreadCount,
            boolean canSend
    ) {}
}

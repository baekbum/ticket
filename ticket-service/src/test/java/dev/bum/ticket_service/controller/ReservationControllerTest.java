package dev.bum.ticket_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.ticket_service.dto.ReservationDto;
import dev.bum.ticket_service.dto.TicketDto;
import dev.bum.ticket_service.enums.*;
import dev.bum.ticket_service.exception.TicketLimitExceededException;
import dev.bum.ticket_service.jpa.event.Event;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.security.JwtAuthenticationFilter;
import dev.bum.ticket_service.security.SecurityConfig;
import dev.bum.ticket_service.service.reservation.ReservationService;
import dev.bum.ticket_service.vo.reservation.CancelReservationInfo;
import dev.bum.ticket_service.vo.reservation.InsertReservationInfo;
import dev.bum.ticket_service.vo.seat.InsertSeatAreaConfig;
import dev.bum.ticket_service.vo.seat.InsertSeatInfo;
import dev.bum.ticket_service.vo.seat.SeatCond;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({JwtAuthenticationFilter.class, SecurityConfig.class})
@WebMvcTest(ReservationController.class)
class ReservationControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private ReservationService reservationService;

    private String domain = "reservation";
    private String apiVersion = "v1";

    private Event event;
    private List<Seat> seatList;

    @BeforeEach
    void info_set_up() throws Exception {
        // 1. 이벤트 정보 등록
        Event event = Event.builder()
                .eventId(0L)
                .artistName("아이유")
                .title("아이유 콘서트")
                .description("올림픽 체조 경기장에서 하는 아이유 콘서트")
                .venue("올림픽 체조 경기장")
                .eventDateTime(LocalDateTime.of(2026, 9, 18, 18, 0))
                .totalSeats(14500)
                .status(EventStatus.ON_SALE)
                .maxTicketsPerPerson(4)
                .build();

        this.event = event;

        // 2. 좌석 정보 등록
        this.seatList = createSeat(event, SeatGrade.VIP, "Floor-A", 2, 5, 168000);
    }

    @Test
    @DisplayName("토큰 값 오류")
    void token_invalid() throws Exception {
        SeatCond cond = SeatCond.builder()
                .page(0)
                .size(10)
                .build();

        mockMvc.perform(post("/api/" + apiVersion + "/" + domain + "/select")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cond)))
                .andExpect(status().is4xxClientError());
    }

    @WithMockUser(username = "user", roles = {"USER"})
    @Test
    @DisplayName("1인 4매 제한 공연에 3좌석을 선택해서 예매하는 경우")
    void reservation_insert_1() throws Exception {
        String userId = "IU";

        List<Long> seatIdList = List.of(
                this.seatList.get(0).getSeatId(),
                this.seatList.get(1).getSeatId(),
                this.seatList.get(2).getSeatId()
        );

        InsertReservationInfo info = InsertReservationInfo.builder()
                .userId(userId)
                .eventId(this.event.getEventId())
                .seatIdList(seatIdList)
                .build();

        mockMvc.perform(post("/api/" + apiVersion + "/" + domain + "/insert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk());
    }

    @WithMockUser(username = "user", roles = {"USER"})
    @Test
    @DisplayName("1인 4매 제한 공연에 2좌석이 예매된 상태에서 2좌석을 추가로 예매하는 경우")
    void reservation_insert_2() throws Exception {
        // 이미 2좌석이 예매된 상태라고 가정
        String userId = "IU";

        List<Long> seatIdList = List.of(
                this.seatList.get(2).getSeatId(),
                this.seatList.get(3).getSeatId()
        );

        InsertReservationInfo info = InsertReservationInfo.builder()
                .userId(userId)
                .eventId(this.event.getEventId())
                .seatIdList(seatIdList)
                .build();

        mockMvc.perform(post("/api/" + apiVersion + "/" + domain + "/insert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk());
    }

    @WithMockUser(username = "user", roles = {"USER"})
    @Test
    @DisplayName("매수 제한은 4매인데, 좌석을 5석 선택한 경우")
    void reservation_insert_fail_over_limit_1() throws Exception {
        String userId = "IU";
        int maxLimit = this.event.getMaxTicketsPerPerson();

        List<Long> seatIdList = List.of(
                this.seatList.get(0).getSeatId(),
                this.seatList.get(1).getSeatId(),
                this.seatList.get(2).getSeatId(),
                this.seatList.get(3).getSeatId(),
                this.seatList.get(4).getSeatId()
        );

        InsertReservationInfo info = InsertReservationInfo.builder()
                .userId(userId)
                .eventId(this.event.getEventId())
                .seatIdList(seatIdList)
                .build();

        doThrow(new TicketLimitExceededException(
                String.format("1인당 최대 예매 가능 수량은 %d매입니다.", maxLimit)
        )).when(reservationService).insert(any(InsertReservationInfo.class));

        mockMvc.perform(post("/api/" + apiVersion + "/" + domain + "/insert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isBadRequest());
    }

    @WithMockUser(username = "user", roles = {"USER"})
    @Test
    @DisplayName("매수 제한은 4매인데, 이미 2좌석을 선택했고 3좌석 이상을 추가적으로 티켓팅 하는 경우")
    void reservation_insert_fail_over_limit_2() throws Exception {
        // 이미 2장의 티켓이 등록된 상태.
        String userId = "IU";
        int maxLimit = this.event.getMaxTicketsPerPerson();

        List<Long> seatIdList = List.of(
                this.seatList.get(2).getSeatId(),
                this.seatList.get(3).getSeatId(),
                this.seatList.get(4).getSeatId()
        );

        InsertReservationInfo info = InsertReservationInfo.builder()
                .userId(userId)
                .eventId(this.event.getEventId())
                .seatIdList(seatIdList)
                .build();

        doThrow(new TicketLimitExceededException(
                String.format("이미 기존 예매 내역이 존재하여, 추가로 %d매를 초과하여 예매할 수 없습니다.", maxLimit)
        )).when(reservationService).insert(any(InsertReservationInfo.class));

        mockMvc.perform(post("/api/" + apiVersion + "/" + domain + "/insert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isBadRequest());
    }

    @WithMockUser(username = "user", roles = {"USER"})
    @Test
    @DisplayName("ID로 예매 내역 조회")
    void reservation_select_by_id() throws Exception {
        String userId = "IU";

        TicketDto dto_1 = createTicketDto(1L, this.seatList.get(0));
        TicketDto dto_2 = createTicketDto(2L, this.seatList.get(1));

        List<TicketDto> dtos = List.of(dto_1, dto_2);
        long reservationId = 2L;

        ReservationDto response = createReservationDto(reservationId, dtos, userId, this.event);

        given(reservationService.selectById(reservationId)).willReturn(response);

        mockMvc.perform(get("/api/" + apiVersion + "/" + domain + "/select/id/" + reservationId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationId").value(reservationId))
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.eventId").value(this.event.getEventId()))
                .andExpect(jsonPath("$.eventTitle").value(this.event.getTitle()))
                .andExpect(jsonPath("$.venue").value(this.event.getVenue()))
                .andExpect(jsonPath("$.ticketCount").value(dtos.size()))
                .andExpect(jsonPath("$.status").value(ReservationStatus.CONFIRMED.name()));
    }

    @WithMockUser(username = "user", roles = {"USER"})
    @Test
    @DisplayName("조건으로 예매 내역 조회")
    void reservation_select_by_cond() throws Exception {
        // 이미 다른 공연 예매 내역이 있다고 가정
        String userId = "IU";

        // 다른 공연
        Event anotherEvent = createAnotherEvent();

        // 다른 공연 좌석 등록
        List<Seat> anotherSeats = createSeat(anotherEvent, SeatGrade.A, "30구역", 2, 5, 88000);

        // 티켓 정보 생성
        TicketDto dto_1 = createTicketDto(3L, anotherSeats.get(0));
        TicketDto dto_2 = createTicketDto(4L, anotherSeats.get(1));
        TicketDto dto_3 = createTicketDto(5L, anotherSeats.get(2));

        List<TicketDto> dtos = List.of(dto_1, dto_2, dto_3);
        long reservationId = 2L;

        ReservationDto response = createReservationDto(reservationId, dtos, userId, anotherEvent);

        given(reservationService.selectById(reservationId)).willReturn(response);

        mockMvc.perform(get("/api/" + apiVersion + "/" + domain + "/select/id/" + reservationId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationId").value(reservationId))
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.eventId").value(anotherEvent.getEventId()))
                .andExpect(jsonPath("$.eventTitle").value(anotherEvent.getTitle()))
                .andExpect(jsonPath("$.venue").value(anotherEvent.getVenue()))
                .andExpect(jsonPath("$.ticketCount").value(dtos.size()))
                .andExpect(jsonPath("$.status").value(ReservationStatus.CONFIRMED.name()));
    }

    @WithMockUser(username = "user", roles = {"USER"})
    @Test
    @DisplayName("예매 내역에 해당하는 전체 티켓을 취소하는 케이스")
    void reservation_cancel_all() throws Exception {
        // 이미 예매 내역이 존재한다고 가정
        String userId = "IU";
        long reservationId = 1L;

        CancelReservationInfo info = CancelReservationInfo.builder()
                .selectedTicketIdList(new ArrayList<>())
                .build();

        // 예메 내역에 해당하는 전체 티켓 취소.
        mockMvc.perform(put("/api/" + apiVersion + "/" + domain + "/cancel/id/" + reservationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk());
        // 1. 예매, 좌석, 티켓 생성

        // 좌석 생성
        List<Seat> seats = createSeat(this.event, SeatGrade.VIP, "Floor-A", 2, 5, 168000);

        // 예매 내역 생성 with 티켓 + 좌석 (3,4,5 번 좌석)
        List<TicketDto> ticketDtos = new ArrayList<>();
        List<Long> selectedSeatId = List.of(3L, 4L, 5L);

        List<Seat> selectedSeats = seats.stream()
                .filter(seat -> selectedSeatId.contains(seat.getSeatId()))
                .toList();

        int initTicketCnt = 0;

        for (Seat seat : selectedSeats) {
            seat.lock(); // 좌석 상태 변경
            ticketDtos.add(createTicketDto(initTicketCnt++, seat));
        }

        ReservationDto response = createReservationDto(reservationId, ticketDtos, userId, this.event);

        // 2. 티켓 취소 + 내부적으로 좌석 취소까지 병행 (상태 변경)
        for (TicketDto ticketDto : ticketDtos) {
            ticketDto.setStatus(TicketStatus.CANCELLED.name());
        }

        for (Seat seat : selectedSeats) {
            seat.available(); // 좌석 상태 변경
        }

        // 3. 예매 취소 (상태 변경)
        response.setStatus(ReservationStatus.CANCELLED.name());

        // 4. 검증
        given(reservationService.selectById(reservationId)).willReturn(response);

        mockMvc.perform(get("/api/" + apiVersion + "/" + domain + "/select/id/" + reservationId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationId").value(reservationId))
                .andExpect(jsonPath("$.status").value(ReservationStatus.CANCELLED.name()));
    }

    @WithMockUser(username = "user", roles = {"USER"})
    @Test
    @DisplayName("예매 내역에 해당하는 부분 티켓을 취소하는 케이스")
    void reservation_cancel_some() throws Exception {
        // 이미 예매 내역이 존재한다고 가정
        String userId = "IU";
        long reservationId = 1L;

        CancelReservationInfo info = CancelReservationInfo.builder()
                .selectedTicketIdList(new ArrayList<>())
                .build();

        // 예메 내역에 해당하는 전체 티켓 취소.
        mockMvc.perform(put("/api/" + apiVersion + "/" + domain + "/cancel/id/" + reservationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(info)))
                .andExpect(status().isOk());
        // 1. 예매, 좌석, 티켓 생성

        // 좌석 생성
        List<Seat> seats = createSeat(this.event, SeatGrade.VIP, "Floor-A", 2, 5, 168000);

        // 예매 내역 생성 with 티켓 + 좌석 (3,4,5 번 좌석)
        List<TicketDto> ticketDtos = new ArrayList<>();
        List<Long> selectedSeatId = List.of(3L, 4L, 5L);

        List<Seat> selectedSeats = seats.stream()
                .filter(seat -> selectedSeatId.contains(seat.getSeatId()))
                .toList();

        int initTicketCnt = 0;

        for (Seat seat : selectedSeats) {
            seat.lock(); // 좌석 상태 변경
            ticketDtos.add(createTicketDto(initTicketCnt++, seat));
        }

        ReservationDto response = createReservationDto(reservationId, ticketDtos, userId, this.event);

        // 2. 티켓 취소 + 내부적으로 좌석 취소까지 병행 (상태 변경)

        // 취소하고 싶은 티켓 상태 변경
        List<Long> ticketIdForCancel = List.of(ticketDtos.get(0).getTicketId(), ticketDtos.get(2).getTicketId());
        ticketDtos.stream()
                .filter(ticketDto -> ticketIdForCancel.contains(ticketDto.getTicketId()))
                .forEach(ticketDto -> ticketDto.setStatus(TicketStatus.CANCELLED.name()));

        // 티켓에 1:1로 묶여있는 좌석 상태 변경
        List<String> seatNumberForCancel = List.of(ticketDtos.get(0).getSeatNumber(), ticketDtos.get(2).getSeatNumber());
        selectedSeats.stream()
                .filter(seat -> seatNumberForCancel.contains(seat.getSeatNumber()))
                .forEach(Seat::available);

        // 3. 예매 부분 취소 (상태 변경)
        response.setStatus(ReservationStatus.PARTIALLY_CANCELLED.name());

        // 4. 검증
        given(reservationService.selectById(reservationId)).willReturn(response);

        mockMvc.perform(get("/api/" + apiVersion + "/" + domain + "/select/id/" + reservationId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationId").value(reservationId))
                .andExpect(jsonPath("$.status").value(ReservationStatus.PARTIALLY_CANCELLED.name()));
    }


    // ====================================================================
    // 메서드 정의
    // ====================================================================
    private Event createAnotherEvent() {
        return Event.builder()
                .eventId(2L)
                .artistName("윤하")
                .title("윤하 콘서트")
                .description("올림픽 핸드볼 경기장에서 하는 윤하 콘서트")
                .venue("올림픽 핸드볼 경기장")
                .eventDateTime(LocalDateTime.of(2026, 12, 25, 18, 0))
                .totalSeats(5003)
                .status(EventStatus.ON_SALE)
                .maxTicketsPerPerson(4)
                .build();
    }

    private List<Seat> createSeat(Event event, SeatGrade seatGrade, String zone, int row, int col, int price) {
        InsertSeatAreaConfig seatConfig = InsertSeatAreaConfig.builder()
                .grade(seatGrade)
                .zone(zone)
                .rows(row)
                .cols(col)
                .price(price)
                .build();

        InsertSeatInfo info = InsertSeatInfo.builder()
                .eventId(event.getEventId())
                .insertSeatAreaConfigs(List.of(seatConfig))
                .build();

        List<Seat> newSeatList = new ArrayList<>();
        long seatId = 0;

        for (InsertSeatAreaConfig config : info.getInsertSeatAreaConfigs()) {
            for (int r = 1; r <= config.getRows(); r++) {
                for (int c = 1; c <= config.getCols(); c++) {
                    String seatNumber = String.format("%s구역-%d열-%d번", config.getZone(), r, c);

                    Seat seat = Seat.builder()
                            .seatId(seatId++)
                            .event(event)
                            .seatNumber(seatNumber)
                            .grade(config.getGrade())
                            .price(config.getPrice())
                            .status(SeatStatus.AVAILABLE)
                            .build();

                    newSeatList.add(seat);
                }
            }
        }

        return newSeatList;
    }

    private TicketDto createTicketDto(long ticketId, Seat seat) {
        return TicketDto.builder()
                .ticketId(ticketId)
                .seatNumber(seat.getSeatNumber())
                .grade(seat.getGrade().name())
                .price(seat.getPrice())
                .status(TicketStatus.READY_TO_PAY.name())
                .build();
    }

    private ReservationDto createReservationDto(long reservationId, List<TicketDto> ticketDtos, String userId, Event event) {
        DateTimeFormatter reservedFormatter = DateTimeFormatter.ofPattern("yyyy년 M월 d일");
        String reservedDate = LocalDateTime.now().format(reservedFormatter);

        DateTimeFormatter eventFormatter = DateTimeFormatter.ofPattern("yyyy년 M월 d일 HH시");
        String eventDateTime = LocalDateTime.now().format(eventFormatter);

        return ReservationDto.builder()
                .reservationId(reservationId)
                .userId(userId)
                .eventId(event.getEventId())
                .eventTitle(event.getTitle())
                .reservedDate(reservedDate)
                .eventDateTime(eventDateTime)
                .venue(event.getVenue())
                .ticketCount(ticketDtos.size())
                .status(ReservationStatus.CONFIRMED.name())
                .tickets(ticketDtos)
                .build();
    }
}
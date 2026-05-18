package com.example.library.unit;

import com.example.library.dto.BorrowResponse;
import com.example.library.exception.*;
import com.example.library.model.*;
import com.example.library.repository.BookRepository;
import com.example.library.repository.BorrowRecordRepository;
import com.example.library.repository.MemberRepository;
import com.example.library.service.BorrowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * UNIT TEST - Service Layer
 */
@ExtendWith(MockitoExtension.class)
class BorrowServiceTest {

    @Mock
    private BorrowRecordRepository borrowRecordRepository;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private BorrowService borrowService;

    private Book sampleBook;
    private Member sampleMember;

    @BeforeEach
    void setUp() {
        sampleBook = new Book("978-0-13-468599-1", "Clean Code", "Robert C. Martin", 3, Genre.TECHNOLOGY);
        sampleBook.setId(1L);
        sampleBook.setAvailableCopies(3);

        sampleMember = new Member("Alice", "alice@example.com", MembershipType.STANDARD);
        sampleMember.setId(1L);
        sampleMember.setActive(true); // Ensure member is active by default
    }

    @Nested
    @DisplayName("borrowBook()")
    class BorrowBookTests {

        @Test
        @DisplayName("should successfully borrow a book when all conditions are met")
        void shouldBorrowBook_WhenAllConditionsMet() {
            // Arrange
            when(memberRepository.findById(1L)).thenReturn(Optional.of(sampleMember));
            when(bookRepository.findById(1L)).thenReturn(Optional.of(sampleBook));
            when(borrowRecordRepository.countActiveBorrowsByMember(1L)).thenReturn(0);
            when(borrowRecordRepository.existsByBookIdAndMemberIdAndStatus(1L, 1L, BorrowStatus.BORROWED))
                    .thenReturn(false);
            when(borrowRecordRepository.save(any(BorrowRecord.class)))
                    .thenAnswer(invocation -> {
                        BorrowRecord record = invocation.getArgument(0);
                        record.setId(1L);
                        return record;
                    });
            when(bookRepository.save(any(Book.class))).thenReturn(sampleBook);

            // Act
            BorrowResponse response = borrowService.borrowBook(1L, 1L);

            // Assert
            assertNotNull(response);
            assertEquals("Clean Code", response.getBookTitle());
            assertEquals("Alice", response.getMemberName());
            assertEquals(BorrowStatus.BORROWED, response.getStatus());

            // Verify interactions
            verify(borrowRecordRepository).save(any(BorrowRecord.class));
            verify(bookRepository).save(any(Book.class));
        }

        @Test
        @DisplayName("should throw MemberNotFoundException when member does not exist")
        void shouldThrow_WhenMemberNotFound() {
            when(memberRepository.findById(99L)).thenReturn(Optional.empty());

            assertThrows(MemberNotFoundException.class,
                    () -> borrowService.borrowBook(1L, 99L));

            verify(borrowRecordRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw when book has no available copies")
        void shouldThrow_WhenNoAvailableCopies() {
            sampleBook.setAvailableCopies(0);
            when(memberRepository.findById(1L)).thenReturn(Optional.of(sampleMember));
            when(bookRepository.findById(1L)).thenReturn(Optional.of(sampleBook));

            assertThrows(BookNotAvailableException.class,
                    () -> borrowService.borrowBook(1L, 1L));
        }

        // =====================================================================
        // TODO: Students should write the remaining borrowBook() tests
        // =====================================================================

        @Test
        @DisplayName("should throw when member has reached borrowing limit")
        void shouldThrow_WhenBorrowLimitReached() {
            // Arrange
            when(memberRepository.findById(1L)).thenReturn(Optional.of(sampleMember));
            when(bookRepository.findById(1L)).thenReturn(Optional.of(sampleBook));

            //  For standard member the limit is 3
            when(borrowRecordRepository.countActiveBorrowsByMember(1L)).thenReturn(3);

            // Act & Assert
            assertThrows(BorrowLimitExceededException.class,
                    () -> borrowService.borrowBook(1L, 1L));

            verify(borrowRecordRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw when member already has this book borrowed")
        void shouldThrow_WhenDuplicateBorrow() {
            // Arrange
            when(memberRepository.findById(1L)).thenReturn(Optional.of(sampleMember));
            when(bookRepository.findById(1L)).thenReturn(Optional.of(sampleBook));
            when(borrowRecordRepository.countActiveBorrowsByMember(1L)).thenReturn(1);

            // Duplicate borrow check true
            when(borrowRecordRepository.existsByBookIdAndMemberIdAndStatus(1L, 1L, BorrowStatus.BORROWED))
                    .thenReturn(true);

            // Act & Assert
            assertThrows(IllegalStateException.class,
                    () -> borrowService.borrowBook(1L, 1L));

            verify(borrowRecordRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw when inactive member tries to borrow")
        void shouldThrow_WhenMemberInactive() {
            // Arrange
            sampleMember.setActive(false);
            when(memberRepository.findById(1L)).thenReturn(Optional.of(sampleMember));

            // Act & Assert
            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    () -> borrowService.borrowBook(1L, 1L));

            assertEquals("Inactive members cannot borrow books", exception.getMessage());
            verify(bookRepository, never()).findById(any());
        }

        @Test
        @DisplayName("should decrease available copies after successful borrow")
        void shouldDecreaseAvailableCopies() {
            // Arrange
            when(memberRepository.findById(1L)).thenReturn(Optional.of(sampleMember));
            when(bookRepository.findById(1L)).thenReturn(Optional.of(sampleBook));
            when(borrowRecordRepository.countActiveBorrowsByMember(1L)).thenReturn(0);
            when(borrowRecordRepository.existsByBookIdAndMemberIdAndStatus(1L, 1L, BorrowStatus.BORROWED))
                    .thenReturn(false);

            BorrowRecord mockRecord = new BorrowRecord(sampleBook, sampleMember);
            mockRecord.setId(1L);
            when(borrowRecordRepository.save(any(BorrowRecord.class))).thenReturn(mockRecord);

            // Act
            borrowService.borrowBook(1L, 1L);

            // Assert
            ArgumentCaptor<Book> bookCaptor = ArgumentCaptor.forClass(Book.class);
            verify(bookRepository).save(bookCaptor.capture());

            Book savedBook = bookCaptor.getValue();
            assertEquals(2, savedBook.getAvailableCopies());
        }
    }

    @Nested
    @DisplayName("returnBook()")
    class ReturnBookTests {

        @Test
        @DisplayName("should successfully return a borrowed book")
        void shouldReturnBook_WhenBorrowed() {
            // Arrange
            BorrowRecord record = new BorrowRecord(sampleBook, sampleMember);
            record.setId(1L);
            record.setStatus(BorrowStatus.BORROWED);

            when(borrowRecordRepository.findById(1L)).thenReturn(Optional.of(record));

            // Act
            BorrowResponse response = borrowService.returnBook(1L);

            // Assert
            assertEquals(BorrowStatus.RETURNED, response.getStatus());
            assertEquals(BorrowStatus.RETURNED, record.getStatus());
            assertNotNull(record.getReturnDate());

            // ArgumentCaptor
            ArgumentCaptor<Book> bookCaptor = ArgumentCaptor.forClass(Book.class);
            verify(bookRepository).save(bookCaptor.capture());

            Book savedBook = bookCaptor.getValue();
            assertEquals(4, savedBook.getAvailableCopies());
        }

        @Test
        @DisplayName("should throw when trying to return an already returned book")
        void shouldThrow_WhenAlreadyReturned() {
            // Arrange
            BorrowRecord record = new BorrowRecord(sampleBook, sampleMember);
            record.setId(1L);
            record.setStatus(BorrowStatus.RETURNED);

            when(borrowRecordRepository.findById(1L)).thenReturn(Optional.of(record));

            // Act & Assert
            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    () -> borrowService.returnBook(1L));

            assertEquals("This book has already been returned", exception.getMessage());
            verify(bookRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw when borrow record not found")
        void shouldThrow_WhenRecordNotFound() {
            // Arrange
            when(borrowRecordRepository.findById(99L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(IllegalStateException.class,
                    () -> borrowService.returnBook(99L));
        }
    }
}
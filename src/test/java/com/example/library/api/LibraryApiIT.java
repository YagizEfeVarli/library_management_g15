package com.example.library.api;

import com.example.library.integration.AbstractIntegrationTest;
import com.example.library.model.*;
import com.example.library.repository.BookRepository;
import com.example.library.repository.BorrowRecordRepository;
import com.example.library.repository.MemberRepository;
import com.example.library.dto.BorrowRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;


/**
 * API TEST (End-to-End)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LibraryApiIT extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private BorrowRecordRepository borrowRecordRepository;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port + "/api";
        borrowRecordRepository.deleteAll();
        bookRepository.deleteAll();
        memberRepository.deleteAll();
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    private Book createTestBook(String isbn, String title, String author) {
        Book book = new Book(isbn, title, author, 3, Genre.TECHNOLOGY);
        return bookRepository.save(book);
    }

    private Member createTestMember(String name, String email, MembershipType type) {
        Member member = new Member(name, email, type);
        return memberRepository.save(member);
    }

    // =========================================================================
    // EXAMPLE: Book API tests — filled in
    // =========================================================================

    @Nested
    @DisplayName("POST /api/books")
    class CreateBookApi {

        @Test
        @DisplayName("should create a book and return 201")
        void shouldCreateBook() {
            Book newBook = new Book("978-0-13-468599-1", "Clean Code", "Robert C. Martin", 3, Genre.TECHNOLOGY);

            ResponseEntity<Book> response = restTemplate.postForEntity(
                    baseUrl + "/books", newBook, Book.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getId()).isNotNull();
            assertThat(response.getBody().getTitle()).isEqualTo("Clean Code");
            assertThat(response.getBody().getAvailableCopies()).isEqualTo(3);
        }

        @Test
        @DisplayName("should return 400 when required fields are missing")
        void shouldReturn400_WhenFieldsMissing() {
            Book invalidBook = new Book(); // no required fields set

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl + "/books", invalidBook, Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("should return 400 when duplicate ISBN")
        void shouldReturn400_WhenDuplicateIsbn() {
            createTestBook("978-0-13-468599-1", "Clean Code", "Robert C. Martin");

            Book duplicate = new Book("978-0-13-468599-1", "Another Book", "Another Author", 2, Genre.FICTION);
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl + "/books", duplicate, Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("GET /api/books")
    class GetBooksApi {

        @Test
        @DisplayName("should return all books")
        void shouldReturnAllBooks() {
            createTestBook("978-1", "Book A", "Author A");
            createTestBook("978-2", "Book B", "Author B");

            ResponseEntity<Book[]> response = restTemplate.getForEntity(
                    baseUrl + "/books", Book[].class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(2);
        }

        @Test
        @DisplayName("should return 404 for non-existent book")
        void shouldReturn404_WhenBookNotFound() {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                    baseUrl + "/books/999", Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // =========================================================================
    // EXAMPLE: Borrow flow — the most important E2E test
    // =========================================================================

    @Nested
    @DisplayName("Borrow Flow (POST /api/borrows)")
    class BorrowFlowApi {

        @Test
        @DisplayName("should complete full borrow-return cycle")
        void shouldCompleteBorrowReturnCycle() {
            // Setup
            Book book = createTestBook("978-1", "Test Book", "Test Author");
            Member member = createTestMember("Alice", "alice@test.com", MembershipType.STANDARD);

            // 1. Borrow the book
            BorrowRequest borrowRequest = new BorrowRequest(book.getId(), member.getId());
            ResponseEntity<Map> borrowResponse = restTemplate.postForEntity(
                    baseUrl + "/borrows", borrowRequest, Map.class);

            assertThat(borrowResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(borrowResponse.getBody()).containsEntry("bookTitle", "Test Book");
            assertThat(borrowResponse.getBody()).containsEntry("memberName", "Alice");
            assertThat(borrowResponse.getBody()).containsEntry("status", "BORROWED");

            Number borrowId = (Number) borrowResponse.getBody().get("id");

            // 2. Verify book availability decreased
            ResponseEntity<Book> bookResponse = restTemplate.getForEntity(
                    baseUrl + "/books/" + book.getId(), Book.class);
            assertThat(bookResponse.getBody().getAvailableCopies()).isEqualTo(2);

            // 3. Return the book
            ResponseEntity<Map> returnResponse = restTemplate.postForEntity(
                    baseUrl + "/borrows/" + borrowId.longValue() + "/return",
                    null, Map.class);

            assertThat(returnResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(returnResponse.getBody()).containsEntry("status", "RETURNED");

            // 4. Verify book availability increased back
            bookResponse = restTemplate.getForEntity(
                    baseUrl + "/books/" + book.getId(), Book.class);
            assertThat(bookResponse.getBody().getAvailableCopies()).isEqualTo(3);
        }
    }

    // =========================================================================

    // =========================================================================

    @Nested
    @DisplayName("POST /api/borrows - Error cases")
    class BorrowErrorsApi {

        @Test
        @DisplayName("should return 409 when borrowing limit exceeded")
        void shouldReturn409_WhenBorrowLimitExceeded() {
            Member member = createTestMember("Student", "student@test.com", MembershipType.STUDENT);

            Book book1 = createTestBook("isbn-1", "Book 1", "Author");
            Book book2 = createTestBook("isbn-2", "Book 2", "Author");
            Book book3 = createTestBook("isbn-3", "Book 3", "Author");

            restTemplate.postForEntity(baseUrl + "/borrows",
                    new BorrowRequest(book1.getId(), member.getId()), Map.class);

            restTemplate.postForEntity(baseUrl + "/borrows",
                    new BorrowRequest(book2.getId(), member.getId()), Map.class);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl + "/borrows",
                    new BorrowRequest(book3.getId(), member.getId()),
                    Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        @DisplayName("should return 409 when no copies available")
        void shouldReturn409_WhenNoCopiesAvailable() {
            Book book = bookRepository.save(
                    new Book("isbn-one-copy", "Single Copy Book", "Author", 1, Genre.TECHNOLOGY)
            );

            Member member1 = createTestMember("Member One", "one@test.com", MembershipType.STANDARD);
            Member member2 = createTestMember("Member Two", "two@test.com", MembershipType.STANDARD);

            restTemplate.postForEntity(baseUrl + "/borrows",
                    new BorrowRequest(book.getId(), member1.getId()), Map.class);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl + "/borrows",
                    new BorrowRequest(book.getId(), member2.getId()),
                    Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        @DisplayName("should return 404 when member does not exist")
        void shouldReturn404_WhenMemberNotFound() {
            Book book = createTestBook("isbn-member-not-found", "Test Book", "Author");

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl + "/borrows",
                    new BorrowRequest(book.getId(), 99999L),
                    Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("should return 404 when book does not exist")
        void shouldReturn404_WhenBookNotFound() {
            Member member = createTestMember("Member", "member@test.com", MembershipType.STANDARD);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl + "/borrows",
                    new BorrowRequest(99999L, member.getId()),
                    Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("Member API")
    class MemberApiTests {

        @Test
        @DisplayName("should create a member and return 201")
        void shouldCreateMember() {
            Member member = new Member("Yigit", "yigit@test.com", MembershipType.STANDARD);

            ResponseEntity<Member> response = restTemplate.postForEntity(
                    baseUrl + "/members",
                    member,
                    Member.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getId()).isNotNull();
            assertThat(response.getBody().getName()).isEqualTo("Yigit");
            assertThat(response.getBody().getEmail()).isEqualTo("yigit@test.com");
            assertThat(response.getBody().isActive()).isTrue();
        }

        @Test
        @DisplayName("should deactivate a member via DELETE")
        void shouldDeactivateMember() {
            Member member = createTestMember("Passive User", "passive@test.com", MembershipType.STANDARD);

            restTemplate.delete(baseUrl + "/members/" + member.getId());

            ResponseEntity<Member> response = restTemplate.getForEntity(
                    baseUrl + "/members/" + member.getId(),
                    Member.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isActive()).isFalse();
        }

        @Test
        @DisplayName("should return 400 when creating member with invalid email")
        void shouldReturn400_WhenInvalidEmail() {
            Member member = new Member("Invalid Email User", "invalid-email", MembershipType.STANDARD);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl + "/members",
                    member,
                    Map.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("Search & Filter API")
    class SearchApiTests {

        @Test
        @DisplayName("should search books by keyword via GET /api/books/search?keyword=...")
        void shouldSearchBooks() {
            createTestBook("search-1", "Java Programming", "John Author");
            createTestBook("search-2", "Python Guide", "Jane Author");
            createTestBook("search-3", "Clean Architecture", "Robert Martin");

            ResponseEntity<Book[]> response = restTemplate.getForEntity(
                    baseUrl + "/books/search?keyword=Java",
                    Book[].class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody()).hasSize(1);
            assertThat(response.getBody()[0].getTitle()).isEqualTo("Java Programming");
        }

        @Test
        @DisplayName("should get active borrows for a member")
        void shouldGetActiveBorrows() {
            Member member = createTestMember("Borrower", "borrower@test.com", MembershipType.STANDARD);
            Book book1 = createTestBook("active-1", "Active Book", "Author");
            Book book2 = createTestBook("active-2", "Returned Book", "Author");

            ResponseEntity<Map> borrow1 = restTemplate.postForEntity(
                    baseUrl + "/borrows",
                    new BorrowRequest(book1.getId(), member.getId()),
                    Map.class);

            ResponseEntity<Map> borrow2 = restTemplate.postForEntity(
                    baseUrl + "/borrows",
                    new BorrowRequest(book2.getId(), member.getId()),
                    Map.class);

            Number returnedBorrowId = (Number) borrow2.getBody().get("id");

            restTemplate.postForEntity(
                    baseUrl + "/borrows/" + returnedBorrowId.longValue() + "/return",
                    null,
                    Map.class);

            ResponseEntity<Map[]> response = restTemplate.getForEntity(
                    baseUrl + "/borrows/member/" + member.getId() + "/active",
                    Map[].class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody()).hasSize(1);
            assertThat(response.getBody()[0]).containsEntry("bookTitle", "Active Book");
        }
    }
}

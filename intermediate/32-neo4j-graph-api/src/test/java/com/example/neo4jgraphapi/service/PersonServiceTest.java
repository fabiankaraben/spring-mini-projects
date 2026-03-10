package com.example.neo4jgraphapi.service;

import com.example.neo4jgraphapi.domain.Movie;
import com.example.neo4jgraphapi.domain.Person;
import com.example.neo4jgraphapi.dto.CreatePersonRequest;
import com.example.neo4jgraphapi.repository.MovieRepository;
import com.example.neo4jgraphapi.repository.PersonRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PersonService}.
 *
 * <p>Uses Mockito to mock the repository layer so these tests run without
 * any Spring context or database. The {@code @ExtendWith(MockitoExtension.class)}
 * annotation wires up Mockito's JUnit 5 integration.</p>
 *
 * <p>Key concepts tested:
 * <ul>
 *   <li>Creating person nodes (DTO → entity mapping)</li>
 *   <li>Querying persons by name and birth year range</li>
 *   <li>Creating graph relationships (ACTED_IN, DIRECTED, FOLLOWS)</li>
 *   <li>Exceptions when referenced nodes don't exist</li>
 * </ul>
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PersonService unit tests")
class PersonServiceTest {

    /** Mock of the PersonRepository — no real Neo4j connection is made. */
    @Mock
    private PersonRepository personRepository;

    /** Mock of the MovieRepository — needed for relationship creation methods. */
    @Mock
    private MovieRepository movieRepository;

    /**
     * InjectMocks creates a real PersonService and injects the mocks above.
     * This simulates the dependency injection that Spring would normally do.
     */
    @InjectMocks
    private PersonService personService;

    /** Shared test fixtures (reusable across multiple tests). */
    private Person keanu;
    private Movie matrix;

    @BeforeEach
    void setUp() {
        // Create reusable test data before each test
        keanu = new Person("Keanu Reeves", 1964);
        keanu.setId(1L);

        matrix = new Movie("The Matrix", 1999);
        matrix.setId(10L);
    }

    // -------------------------------------------------------------------------
    // createPerson tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("createPerson should map DTO to entity and save it")
    void createPerson_mapsAndSaves() {
        // Arrange: define what the mock repository returns when save() is called
        CreatePersonRequest request = new CreatePersonRequest("Keanu Reeves", 1964);
        when(personRepository.save(any(Person.class))).thenReturn(keanu);

        // Act
        Person result = personService.createPerson(request);

        // Assert: the returned person matches what the repository returned
        assertThat(result.getName()).isEqualTo("Keanu Reeves");
        assertThat(result.getBorn()).isEqualTo(1964);

        // Verify: the repository save() was called exactly once
        verify(personRepository, times(1)).save(any(Person.class));
    }

    @Test
    @DisplayName("createPerson with null born should still save successfully")
    void createPerson_withNullBorn_saves() {
        CreatePersonRequest request = new CreatePersonRequest("Unknown Person", null);
        Person personWithNullBorn = new Person("Unknown Person", null);
        when(personRepository.save(any(Person.class))).thenReturn(personWithNullBorn);

        Person result = personService.createPerson(request);

        assertThat(result.getName()).isEqualTo("Unknown Person");
        assertThat(result.getBorn()).isNull();
    }

    // -------------------------------------------------------------------------
    // findAll tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findAll should return all persons from repository")
    void findAll_returnsAllPersons() {
        // Arrange
        Person laurence = new Person("Laurence Fishburne", 1961);
        when(personRepository.findAll()).thenReturn(List.of(keanu, laurence));

        // Act
        List<Person> result = personService.findAll();

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result).extracting(Person::getName)
                .containsExactlyInAnyOrder("Keanu Reeves", "Laurence Fishburne");
    }

    @Test
    @DisplayName("findAll should return empty list when no persons exist")
    void findAll_returnsEmptyList_whenNoneExist() {
        when(personRepository.findAll()).thenReturn(List.of());

        List<Person> result = personService.findAll();

        assertThat(result).isEmpty();
    }

    // -------------------------------------------------------------------------
    // findById tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findById should return person when found")
    void findById_returnsPersonWhenFound() {
        when(personRepository.findById(1L)).thenReturn(Optional.of(keanu));

        Optional<Person> result = personService.findById(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Keanu Reeves");
    }

    @Test
    @DisplayName("findById should return empty when not found")
    void findById_returnsEmptyWhenNotFound() {
        when(personRepository.findById(999L)).thenReturn(Optional.empty());

        Optional<Person> result = personService.findById(999L);

        assertThat(result).isEmpty();
    }

    // -------------------------------------------------------------------------
    // findByName tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findByName should return matching person")
    void findByName_returnsMatchingPerson() {
        when(personRepository.findByName("Keanu Reeves")).thenReturn(Optional.of(keanu));

        Optional<Person> result = personService.findByName("Keanu Reeves");

        assertThat(result).isPresent();
        assertThat(result.get().getBorn()).isEqualTo(1964);
    }

    // -------------------------------------------------------------------------
    // findByBornBetween tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findByBornBetween should return persons in range")
    void findByBornBetween_returnsPersonsInRange() {
        Person lana = new Person("Lana Wachowski", 1965);
        when(personRepository.findByBornBetween(1960, 1970)).thenReturn(List.of(keanu, lana));

        List<Person> result = personService.findByBornBetween(1960, 1970);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Person::getBorn).allMatch(born -> born >= 1960 && born <= 1970);
    }

    // -------------------------------------------------------------------------
    // addActedIn tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("addActedIn should add movie to person's actedIn list and save")
    void addActedIn_addsMovieAndSaves() {
        // Arrange: both person and movie exist
        when(personRepository.findByName("Keanu Reeves")).thenReturn(Optional.of(keanu));
        when(movieRepository.findByTitle("The Matrix")).thenReturn(Optional.of(matrix));
        when(personRepository.save(keanu)).thenReturn(keanu);

        // Act: create the ACTED_IN relationship
        Person result = personService.addActedIn("Keanu Reeves", "The Matrix");

        // Assert: matrix is now in the person's actedIn list
        assertThat(result.getActedIn()).contains(matrix);
        verify(personRepository).save(keanu);
    }

    @Test
    @DisplayName("addActedIn should throw when person does not exist")
    void addActedIn_throwsWhenPersonNotFound() {
        when(personRepository.findByName("Unknown")).thenReturn(Optional.empty());

        // Assert: IllegalArgumentException is thrown with the expected message
        assertThatThrownBy(() -> personService.addActedIn("Unknown", "The Matrix"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Person not found");
    }

    @Test
    @DisplayName("addActedIn should throw when movie does not exist")
    void addActedIn_throwsWhenMovieNotFound() {
        when(personRepository.findByName("Keanu Reeves")).thenReturn(Optional.of(keanu));
        when(movieRepository.findByTitle("Unknown Movie")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> personService.addActedIn("Keanu Reeves", "Unknown Movie"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Movie not found");
    }

    @Test
    @DisplayName("addActedIn should not add duplicate movies")
    void addActedIn_doesNotAddDuplicates() {
        // Arrange: matrix is already in the actedIn list
        keanu.getActedIn().add(matrix);
        when(personRepository.findByName("Keanu Reeves")).thenReturn(Optional.of(keanu));
        when(movieRepository.findByTitle("The Matrix")).thenReturn(Optional.of(matrix));
        when(personRepository.save(keanu)).thenReturn(keanu);

        // Act: try to add the same movie again
        Person result = personService.addActedIn("Keanu Reeves", "The Matrix");

        // Assert: still only one entry (no duplicate)
        assertThat(result.getActedIn()).hasSize(1);
    }

    // -------------------------------------------------------------------------
    // addFollows tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("addFollows should create FOLLOWS edge between two persons")
    void addFollows_createsSocialEdge() {
        Person laurence = new Person("Laurence Fishburne", 1961);
        laurence.setId(2L);

        when(personRepository.findByName("Keanu Reeves")).thenReturn(Optional.of(keanu));
        when(personRepository.findByName("Laurence Fishburne")).thenReturn(Optional.of(laurence));
        when(personRepository.save(keanu)).thenReturn(keanu);

        // Act: (keanu)-[:FOLLOWS]->(laurence)
        Person result = personService.addFollows("Keanu Reeves", "Laurence Fishburne");

        assertThat(result.getFollows()).contains(laurence);
        verify(personRepository).save(keanu);
    }

    @Test
    @DisplayName("addFollows should throw when follower does not exist")
    void addFollows_throwsWhenFollowerNotFound() {
        when(personRepository.findByName("Ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> personService.addFollows("Ghost", "Laurence Fishburne"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Person not found: Ghost");
    }

    // -------------------------------------------------------------------------
    // deletePerson tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("deletePerson should call deleteById on the repository")
    void deletePerson_callsDeleteById() {
        // No return value — deleteById is void
        doNothing().when(personRepository).deleteById(1L);

        personService.deletePerson(1L);

        // Verify the repository method was called with the correct ID
        verify(personRepository, times(1)).deleteById(1L);
    }
}

package com.example.neo4jgraphapi.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link Person} domain entity.
 *
 * <p>These tests verify the domain logic in isolation — no Spring context,
 * no database. JUnit 5 + AssertJ is used for readability.</p>
 *
 * <p>Testing domain objects is important in graph models because the
 * relationship lists (actedIn, directed, follows) drive what gets
 * persisted in Neo4j when the entity is saved.</p>
 */
@DisplayName("Person domain entity tests")
class PersonTest {

    @Test
    @DisplayName("Constructor should set name and born correctly")
    void constructor_setsNameAndBorn() {
        // Arrange + Act: create person using convenience constructor
        Person person = new Person("Tom Hanks", 1956);

        // Assert: verify both fields were set correctly
        assertThat(person.getName()).isEqualTo("Tom Hanks");
        assertThat(person.getBorn()).isEqualTo(1956);
    }

    @Test
    @DisplayName("No-arg constructor should create person with null fields")
    void noArgConstructor_createsPersonWithNullFields() {
        // The no-arg constructor is required by Spring Data Neo4j OGM
        Person person = new Person();

        assertThat(person.getName()).isNull();
        assertThat(person.getBorn()).isNull();
        assertThat(person.getId()).isNull();
    }

    @Test
    @DisplayName("New person should have empty relationship lists")
    void newPerson_hasEmptyRelationshipLists() {
        // Graph relationship lists must be initialized (not null) to safely add() to them
        Person person = new Person("Test Person", 1990);

        assertThat(person.getActedIn()).isNotNull().isEmpty();
        assertThat(person.getDirected()).isNotNull().isEmpty();
        assertThat(person.getFollows()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("Adding a movie to actedIn list should increase its size")
    void addActedIn_increasesList() {
        // Arrange
        Person person = new Person("Keanu Reeves", 1964);
        Movie matrix = new Movie("The Matrix", 1999);
        Movie johnWick = new Movie("John Wick", 2014);

        // Act: simulate adding ACTED_IN relationships
        person.getActedIn().add(matrix);
        person.getActedIn().add(johnWick);

        // Assert: both movies are in the list
        assertThat(person.getActedIn()).hasSize(2);
        assertThat(person.getActedIn()).contains(matrix, johnWick);
    }

    @Test
    @DisplayName("Adding a movie to directed list should increase its size")
    void addDirected_increasesList() {
        // Arrange
        Person director = new Person("Lana Wachowski", 1965);
        Movie matrix = new Movie("The Matrix", 1999);

        // Act
        director.getDirected().add(matrix);

        // Assert
        assertThat(director.getDirected()).hasSize(1);
        assertThat(director.getDirected()).contains(matrix);
    }

    @Test
    @DisplayName("Adding a person to follows list should create directed social edge")
    void addFollows_addsPersonToFollowsList() {
        // Arrange
        Person follower = new Person("Alice", 1990);
        Person followed = new Person("Bob", 1985);

        // Act: (Alice)-[:FOLLOWS]->(Bob)
        follower.getFollows().add(followed);

        // Assert
        assertThat(follower.getFollows()).hasSize(1);
        assertThat(follower.getFollows()).contains(followed);
    }

    @Test
    @DisplayName("Setter methods should update fields correctly")
    void setters_updateFieldsCorrectly() {
        // Arrange
        Person person = new Person();

        // Act
        person.setName("Updated Name");
        person.setBorn(2000);
        person.setId(42L);

        // Assert
        assertThat(person.getName()).isEqualTo("Updated Name");
        assertThat(person.getBorn()).isEqualTo(2000);
        assertThat(person.getId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("setActedIn should replace the entire list")
    void setActedIn_replacesEntireList() {
        // Arrange
        Person person = new Person("Actor", 1970);
        Movie movie1 = new Movie("Movie 1", 2000);
        Movie movie2 = new Movie("Movie 2", 2001);
        person.getActedIn().add(movie1);

        // Act: replace with a new list
        person.setActedIn(List.of(movie2));

        // Assert: only movie2 remains
        assertThat(person.getActedIn()).hasSize(1);
        assertThat(person.getActedIn()).contains(movie2);
    }
}

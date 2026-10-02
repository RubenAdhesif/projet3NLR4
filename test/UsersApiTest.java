/*
 * OpenAPI user management
 * Small API to test OpenAPI usage
 *
 * The version of the OpenAPI document: 1.0.0
 *
 * Test class provided for Lab 3: it replaces the UsersApiTest class generated in
 * client/src/test/java/org/openapitools/client/api/.
 *
 * IMPORTANT: the server (http://localhost:8080/api) must be running before
 * executing "mvn test" in the client project.
 */


package org.openapitools.client.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openapitools.client.ApiException;
import org.openapitools.client.model.User;

import java.util.Arrays;
import java.util.List;

/**
 * API tests for UsersApi
 */
public class UsersApiTest {

    private final UsersApi api = new UsersApi();

    /**
     * Removes the users used by the tests so that each test starts from a known
     * state, whatever the result of the previous tests (404 = already absent).
     */
    @BeforeEach
    public void cleanUp() throws ApiException {
        for (Long id : Arrays.asList(1L, 2L)) {
            try {
                api.deleteUserById(id);
            } catch (ApiException e) {
                if (e.getCode() != 404) {
                    throw e;
                }
            }
        }
    }

    /**
     * Create a user
     *
     * @throws ApiException if the Api call fails
     */
    @Test
    public void createUserTest() throws ApiException {
        User createdUser = api.createUser(new User().id(1L).name("paul"));
        assertNotNull(createdUser);
        assertEquals(1L, createdUser.getId());
        assertEquals("paul", createdUser.getName());
    }

    /**
     * Create a user twice: a 409 (Conflict) error is expected
     * (example of error case test)
     */
    @Test
    public void createExistingUserTest() throws ApiException {
        User user = new User().id(1L).name("paul");
        api.createUser(user);
        ApiException e = assertThrows(ApiException.class, () -> api.createUser(user));
        assertEquals(409, e.getCode());
    }

    /**
     * Delete a specific user
     *
     * @throws ApiException if the Api call fails
     */
    @Test
    public void deleteUserByIdTest() throws ApiException {
        api.createUser(new User().id(1L).name("paul"));
        Boolean response = api.deleteUserById(1L);
        assertTrue(response);
    }

    /**
     * List all users
     *
     * @throws ApiException if the Api call fails
     */
    @Test
    public void listUsersTest() throws ApiException {
        api.createUser(new User().id(1L).name("paul"));
        api.createUser(new User().id(2L).name("pierre"));
        List<User> userList = api.listUsers();
        assertEquals(2, userList.size());
    }

    /**
     * Info for a specific user
     *
     * @throws ApiException if the Api call fails
     */
    @Test
    public void showUserByIdTest() throws ApiException {
        api.createUser(new User().id(1L).name("paul"));
        User userInfo = api.showUserById(1L);
        assertNotNull(userInfo);
        assertEquals(1L, userInfo.getId());
        assertEquals("paul", userInfo.getName());
    }

    /**
     * Update a specific user
     *
     * @throws ApiException if the Api call fails
     */
    @Test
    public void updateUserTest() throws ApiException {
        api.createUser(new User().id(1L).name("paul"));
        User updatedUser = api.updateUser(1L, new User().id(1L).name("jacques"));
        assertEquals(1L, updatedUser.getId());
        assertEquals("jacques", updatedUser.getName());
        assertEquals("jacques", api.showUserById(1L).getName());
    }

    // TODO (students): add the other error cases described in the documentation, e.g.
    //  - retrieving an unknown user (404)
    //  - deleting an unknown user (404)
    //  - updating an unknown user (404)

}

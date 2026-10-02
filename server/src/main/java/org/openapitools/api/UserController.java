package org.openapitools.api;

import org.openapitools.model.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class UserController implements UsersUser {

    // Stockage en mémoire des utilisateurs (clé: id, valeur: User)
    private final Map<Long, User> users = new HashMap<>();

    @Override
    public ResponseEntity<User> createUser(User user) {
        if (user == null || user.getId() == null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        // Vérifie si l'utilisateur existe déjà
        if (users.containsKey(user.getId())) {
            return new ResponseEntity<>(HttpStatus.CONFLICT); // 409
        }
        users.put(user.getId(), user);
        return new ResponseEntity<>(user, HttpStatus.CREATED); // 201
    }

    @Override
    public ResponseEntity<List<User>> listUsers() {
        return new ResponseEntity<>(new ArrayList<>(users.values()), HttpStatus.OK); // 200
    }

    @Override
    public ResponseEntity<User> showUserById(Long id) {
        User user = users.get(id);
        if (user == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND); // 404
        }
        return new ResponseEntity<>(user, HttpStatus.OK); // 200
    }

    @Override
    public ResponseEntity<User> updateUser(Long id, User user) {
        if (!users.containsKey(id)) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND); // 404
        }
        // L'id de l'URL est conservé, les autres propriétés sont mises à jour
        user.setId(id);
        users.put(id, user);
        return new ResponseEntity<>(user, HttpStatus.OK); // 200
    }

    @Override
    public ResponseEntity<Boolean> deleteUserById(Long id) {
        if (!users.containsKey(id)) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND); // 404
        }
        users.remove(id);
        return new ResponseEntity<>(true, HttpStatus.OK); // 200
    }
}
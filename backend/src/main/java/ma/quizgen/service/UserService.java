package ma.quizgen.service;

import ma.quizgen.dto.UserDto;
import org.springframework.data.domain.Pageable;

import ma.quizgen.dto.PageResponse;
import java.util.UUID;

public interface UserService {

    /**
     * Synchronise l'utilisateur Keycloak dans la base de données.
     * Crée l'entrée si elle n'existe pas, met à jour prénom/nom/rôle sinon.
     *
     * @return le profil synchronisé
     */
    UserDto syncCurrentUser();

    /**
     * Retourne le profil de l'utilisateur actuellement authentifié.
     */
    UserDto getCurrentUserProfile();

    /**
     * Liste tous les utilisateurs (ADMIN uniquement).
     */
    PageResponse<UserDto> listAllUsers(Pageable pageable);

    /**
     * Retourne un utilisateur par son ID (ADMIN uniquement).
     */
    UserDto getUserById(UUID id);
}

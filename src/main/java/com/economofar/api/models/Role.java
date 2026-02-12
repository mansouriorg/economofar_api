package com.economofar.api.models;

/**
 * Enumération des rôles utilisateurs.
 * Note : Le préfixe ROLE_ est le standard Spring Security pour les autorités.
 */
public enum Role {
    ROLE_ADMIN,
    ROLE_STAFF,
    ROLE_USER
}
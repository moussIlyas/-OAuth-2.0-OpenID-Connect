package com.OIDCProject;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityTests {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void endpointPublicSansToken() throws Exception {
        mockMvc.perform(get("/api/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("API publique accessible"));
    }

    @Test
    void demandeSansTokenRefusee() throws Exception {
        mockMvc.perform(get("/api/demandes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenInvalideRefuse() throws Exception {
        when(jwtDecoder.decode("token-invalide")).thenThrow(new BadJwtException("token invalide"));

        mockMvc.perform(get("/api/demandes")
                        .header("Authorization", "Bearer token-invalide"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userPeutConsulterSesDemandes() throws Exception {
        mockMvc.perform(get("/api/demandes")
                        .with(jwt().jwt(j -> j.subject("alice-id"))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk());
    }

    @Test
    void userNePeutPasAccederAuxRessourcesAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/demandes")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void userNePeutPasModifierStatut() throws Exception {
        mockMvc.perform(patch("/api/admin/demandes/1")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statut\":\"VALIDEE\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminPeutConsulterToutesLesDemandes() throws Exception {
        mockMvc.perform(get("/api/admin/demandes")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void adminPeutModifierStatut() throws Exception {
        // Aucune demande n'existe en memoire (etat vierge), on verifie donc
        // uniquement que l'autorisation ADMIN est acceptee : 404 attendu (demande introuvable),
        // et non 403 (acces refuse).
        mockMvc.perform(patch("/api/admin/demandes/1")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statut\":\"VALIDEE\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void profilAuthentifieRetourneLesClaimsDuJwt() throws Exception {
        mockMvc.perform(get("/api/profil")
                        .with(jwt().jwt(j -> j.subject("alice-id")
                                        .claim("preferred_username", "alice"))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("alice-id"))
                .andExpect(jsonPath("$.username").value("alice"));
    }
}

package co.edu.unbosque.biohub.consultapublica.controller;

import java.util.UUID;

/** Elemento del listado público (contrato 04, §4.1). Sin coordenadas. */
public record FichaResumenResponse(UUID id, String nombreCientifico, String nombreComun, String familia,
                                   String estadoConservacion, String departamento, String departamentoCodigo,
                                   String region) {
}

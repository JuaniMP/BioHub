/** Punto de entrada del módulo de ubicación (D17). Santiago solo importa de aquí. */
import './ubicacion.css';
export { default as PaginaBusqueda } from './PaginaBusqueda.jsx';
export { default as PaginaMapa } from './PaginaMapa.jsx';
export { default as AccesoDenegado } from './AccesoDenegado.jsx';
export { crearClienteUbicacion } from './api/ubicacionApi.js';

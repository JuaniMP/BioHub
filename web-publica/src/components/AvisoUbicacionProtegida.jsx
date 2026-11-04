import { IconoUbicacionProtegida } from './Iconos.jsx';

/**
 * Aviso de ubicación protegida (02 §6, RNF7). Siempre visible: no es plegable ni emergente
 * y se muestra aunque el servidor enviara `ubicacionProtegida: false` (D14).
 */
export default function AvisoUbicacionProtegida({ variante = 'completo', estadoConservacion }) {
  if (variante === 'breve') {
    return (
      <p className="aviso-ubicacion-breve">
        Por seguridad, BioHub no publica ubicaciones exactas, solo el departamento y la región.
      </p>
    );
  }
  return (
    <aside role="note" className="aviso-ubicacion" aria-labelledby="titulo-ubicacion-protegida">
      <IconoUbicacionProtegida />
      <div>
        <h3 id="titulo-ubicacion-protegida">Ubicación protegida</h3>
        <p>
          Por seguridad, BioHub no publica el lugar exacto donde se encontró esta especie. Si se supiera dónde está,
          personas dedicadas a la caza o al tráfico ilegal podrían llegar hasta ella y ponerla en mayor peligro. Por eso
          solo te mostramos el departamento y la región.
        </p>
        {estadoConservacion === 'EN_PELIGRO' ? (
          <p>Esta especie está en peligro de extinción, así que protegerla es todavía más importante.</p>
        ) : null}
        <p>Las personas investigadoras autorizadas sí pueden consultar la ubicación exacta dentro de la plataforma interna.</p>
      </div>
    </aside>
  );
}

/** Iconos decorativos (siempre aria-hidden; el significado va en el texto). */
const base = { width: 20, height: 20, viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', strokeWidth: 2, strokeLinecap: 'round', strokeLinejoin: 'round', 'aria-hidden': 'true', focusable: 'false', className: 'icono' };

export const IconoAdvertencia = () => (<svg {...base}><path d="M12 3 2 21h20L12 3Z" /><path d="M12 10v5M12 18v.5" /></svg>);
export const IconoVerificado = () => (<svg {...base}><circle cx="12" cy="12" r="9" /><path d="m8 12 3 3 5-6" /></svg>);
export const IconoPregunta = () => (<svg {...base}><circle cx="12" cy="12" r="9" /><path d="M9.5 9.5a2.5 2.5 0 1 1 3.5 2.3c-.7.4-1 .9-1 1.7M12 17v.5" /></svg>);
export const IconoLupa = () => (<svg {...base} width="40" height="40"><circle cx="11" cy="11" r="7" /><path d="m20 20-4-4" /></svg>);
export const IconoInfo = () => (<svg {...base}><circle cx="12" cy="12" r="9" /><path d="M12 11v6M12 7.5v.5" /></svg>);
export const IconoUbicacionProtegida = () => (
  <svg {...base} width="28" height="28">
    <path d="M12 21s-6-5.2-6-10a6 6 0 0 1 12 0c0 4.8-6 10-6 10Z" />
    <rect x="9.5" y="9.5" width="5" height="4" rx="0.8" />
    <path d="M10.5 9.5v-1a1.5 1.5 0 0 1 3 0v1" />
  </svg>
);

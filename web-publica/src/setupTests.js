import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';

// Vitest sin `globals`: la limpieza automática de Testing Library no se registra sola.
afterEach(() => cleanup());

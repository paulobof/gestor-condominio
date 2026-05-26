import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { AuthProvider } from '@/features/auth/AuthProvider';
import { AppRouter } from './router';
import { Toaster } from '@/components/ui/sonner';
import './design-system/tokens.css';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AuthProvider>
      <AppRouter />
      <Toaster />
    </AuthProvider>
  </StrictMode>
);

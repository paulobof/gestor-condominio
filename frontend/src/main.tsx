import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { AuthProvider } from '@/features/auth/AuthProvider';
import { ThemeProvider } from '@/components/theme/ThemeProvider';
import { AppRouter } from './router';
import { Toaster } from '@/components/ui/sonner';
import './design-system/tokens.css';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ThemeProvider>
      <AuthProvider>
        <AppRouter />
        <Toaster />
      </AuthProvider>
    </ThemeProvider>
  </StrictMode>
);

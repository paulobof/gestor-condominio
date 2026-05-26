import { Navigate } from 'react-router-dom';
import { useAuth } from '@/features/auth/useAuth';
import { FullPageSpinner } from './FullPageSpinner';
import type { ReactNode } from 'react';

export function ProtectedRoute({ children }: { children: ReactNode }) {
  const { status } = useAuth();
  if (status === 'loading') return <FullPageSpinner />;
  if (status === 'unauthenticated') return <Navigate to="/login" replace />;
  return <>{children}</>;
}

import { createBrowserRouter, RouterProvider } from 'react-router-dom';
import { LoginPage } from '@/features/auth/pages/LoginPage';
import { RegisterMasterPage } from '@/features/auth/pages/RegisterMasterPage';
import { PendingApprovalPage } from '@/features/auth/pages/PendingApprovalPage';
import { ForgotPasswordPage } from '@/features/auth/pages/ForgotPasswordPage';
import { ResetPasswordPage } from '@/features/auth/pages/ResetPasswordPage';
import { PrivacyPage } from '@/features/privacy/pages/PrivacyPage';
import { ProtectedRoute } from '@/components/ProtectedRoute';
import { PendingRegistrationsPage } from '@/features/admin/pages/PendingRegistrationsPage';
import App from './App';

const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  { path: '/register-master', element: <RegisterMasterPage /> },
  { path: '/pending-approval', element: <PendingApprovalPage /> },
  { path: '/forgot-password', element: <ForgotPasswordPage /> },
  { path: '/reset', element: <ResetPasswordPage /> },
  {
    path: '/',
    element: (
      <ProtectedRoute>
        <App />
      </ProtectedRoute>
    ),
  },
  {
    path: '/admin/registrations',
    element: (
      <ProtectedRoute>
        <PendingRegistrationsPage />
      </ProtectedRoute>
    ),
  },
  {
    path: '/privacidade',
    element: (
      <ProtectedRoute>
        <PrivacyPage />
      </ProtectedRoute>
    ),
  },
]);

export function AppRouter() {
  return <RouterProvider router={router} />;
}

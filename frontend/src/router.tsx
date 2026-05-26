import { createBrowserRouter, RouterProvider } from 'react-router-dom';
import { LoginPage } from '@/features/auth/pages/LoginPage';
import { RegisterMasterPage } from '@/features/auth/pages/RegisterMasterPage';
import { PendingApprovalPage } from '@/features/auth/pages/PendingApprovalPage';
import { ProtectedRoute } from '@/components/ProtectedRoute';
import { PendingRegistrationsPage } from '@/features/admin/pages/PendingRegistrationsPage';
import App from './App';

const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  { path: '/register-master', element: <RegisterMasterPage /> },
  { path: '/pending-approval', element: <PendingApprovalPage /> },
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
]);

export function AppRouter() {
  return <RouterProvider router={router} />;
}

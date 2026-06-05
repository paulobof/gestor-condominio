import { createBrowserRouter, RouterProvider } from 'react-router-dom';
import { LoginPage } from '@/features/auth/pages/LoginPage';
import { RegisterMasterPage } from '@/features/auth/pages/RegisterMasterPage';
import { PendingApprovalPage } from '@/features/auth/pages/PendingApprovalPage';
import { ForgotPasswordPage } from '@/features/auth/pages/ForgotPasswordPage';
import { ResetPasswordPage } from '@/features/auth/pages/ResetPasswordPage';
import { PrivacyPage } from '@/features/privacy/pages/PrivacyPage';
import { ProtectedRoute } from '@/components/ProtectedRoute';
import { PendingRegistrationsPage } from '@/features/admin/pages/PendingRegistrationsPage';
import { ClassifiedsListPage } from '@/features/classifieds/pages/ClassifiedsListPage';
import { ClassifiedDetailPage } from '@/features/classifieds/pages/ClassifiedDetailPage';
import { ClassifiedFormPage } from '@/features/classifieds/pages/ClassifiedFormPage';
import { RecommendationsListPage } from '@/features/recommendations/pages/RecommendationsListPage';
import { RecommendationDetailPage } from '@/features/recommendations/pages/RecommendationDetailPage';
import { RecommendationFormPage } from '@/features/recommendations/pages/RecommendationFormPage';
import { PendingConsentPage } from '@/features/recommendations/pages/PendingConsentPage';
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
  {
    path: '/classificados',
    element: (
      <ProtectedRoute>
        <ClassifiedsListPage />
      </ProtectedRoute>
    ),
  },
  {
    path: '/classificados/novo',
    element: (
      <ProtectedRoute>
        <ClassifiedFormPage />
      </ProtectedRoute>
    ),
  },
  {
    path: '/classificados/:id',
    element: (
      <ProtectedRoute>
        <ClassifiedDetailPage />
      </ProtectedRoute>
    ),
  },
  {
    path: '/classificados/:id/editar',
    element: (
      <ProtectedRoute>
        <ClassifiedFormPage />
      </ProtectedRoute>
    ),
  },
  {
    path: '/indicacoes',
    element: (
      <ProtectedRoute>
        <RecommendationsListPage />
      </ProtectedRoute>
    ),
  },
  {
    path: '/indicacoes/nova',
    element: (
      <ProtectedRoute>
        <RecommendationFormPage />
      </ProtectedRoute>
    ),
  },
  {
    path: '/indicacoes/pendentes',
    element: (
      <ProtectedRoute>
        <PendingConsentPage />
      </ProtectedRoute>
    ),
  },
  {
    path: '/indicacoes/:id',
    element: (
      <ProtectedRoute>
        <RecommendationDetailPage />
      </ProtectedRoute>
    ),
  },
  {
    path: '/indicacoes/:id/editar',
    element: (
      <ProtectedRoute>
        <RecommendationFormPage />
      </ProtectedRoute>
    ),
  },
]);

export function AppRouter() {
  return <RouterProvider router={router} />;
}

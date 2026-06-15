import { createBrowserRouter, RouterProvider } from 'react-router-dom';
import { LoginPage } from '@/features/auth/pages/LoginPage';
import { RegisterMasterPage } from '@/features/auth/pages/RegisterMasterPage';
import { PendingApprovalPage } from '@/features/auth/pages/PendingApprovalPage';
import { ForgotPasswordPage } from '@/features/auth/pages/ForgotPasswordPage';
import { ResetPasswordPage } from '@/features/auth/pages/ResetPasswordPage';
import { PrivacyPage } from '@/features/privacy/pages/PrivacyPage';
import { ProtectedRoute } from '@/components/ProtectedRoute';
import { Shell } from '@/components/layout/Shell';
import { PendingRegistrationsPage } from '@/features/admin/pages/PendingRegistrationsPage';
import { ClassifiedsListPage } from '@/features/classifieds/pages/ClassifiedsListPage';
import { ClassifiedDetailPage } from '@/features/classifieds/pages/ClassifiedDetailPage';
import { ClassifiedFormPage } from '@/features/classifieds/pages/ClassifiedFormPage';
import { RecommendationsListPage } from '@/features/recommendations/pages/RecommendationsListPage';
import { RecommendationDetailPage } from '@/features/recommendations/pages/RecommendationDetailPage';
import { RecommendationFormPage } from '@/features/recommendations/pages/RecommendationFormPage';
import { AnnouncementsListPage } from '@/features/announcements/pages/AnnouncementsListPage';
import { AnnouncementDetailPage } from '@/features/announcements/pages/AnnouncementDetailPage';
import { AnnouncementFormPage } from '@/features/announcements/pages/AnnouncementFormPage';
import { FaqPage } from '@/features/faq/pages/FaqPage';
import { FaqAdminPage } from '@/features/faq/pages/FaqAdminPage';
import { InfoPage } from '@/features/generalinfo/pages/InfoPage';
import { InfoAdminPage } from '@/features/generalinfo/pages/InfoAdminPage';
import { AccessManagementPage } from '@/features/access/pages/AccessManagementPage';
import { MyUnitMembersPage } from '@/features/units/pages/MyUnitMembersPage';
import { ParkingRentalsListPage } from '@/features/parking-rentals/pages/ParkingRentalsListPage';
import { ParkingRentalDetailPage } from '@/features/parking-rentals/pages/ParkingRentalDetailPage';
import { ParkingRentalFormPage } from '@/features/parking-rentals/pages/ParkingRentalFormPage';
import { DocumentsPage } from '@/features/documents/pages/DocumentsPage';
import App from './App';

const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  { path: '/register-master', element: <RegisterMasterPage /> },
  { path: '/pending-approval', element: <PendingApprovalPage /> },
  { path: '/forgot-password', element: <ForgotPasswordPage /> },
  { path: '/reset', element: <ResetPasswordPage /> },
  {
    // Casca autenticada (top bar + bottom-nav). Todas as rotas internas vivem aqui.
    element: (
      <ProtectedRoute>
        <Shell />
      </ProtectedRoute>
    ),
    children: [
      { path: '/', element: <App /> },
      { path: '/admin/registrations', element: <PendingRegistrationsPage /> },
      { path: '/privacidade', element: <PrivacyPage /> },
      { path: '/classificados', element: <ClassifiedsListPage /> },
      { path: '/classificados/novo', element: <ClassifiedFormPage /> },
      { path: '/classificados/:id', element: <ClassifiedDetailPage /> },
      { path: '/classificados/:id/editar', element: <ClassifiedFormPage /> },
      { path: '/indicacoes', element: <RecommendationsListPage /> },
      { path: '/indicacoes/nova', element: <RecommendationFormPage /> },
      { path: '/indicacoes/:id', element: <RecommendationDetailPage /> },
      { path: '/indicacoes/:id/editar', element: <RecommendationFormPage /> },
      { path: '/avisos', element: <AnnouncementsListPage /> },
      { path: '/avisos/novo', element: <AnnouncementFormPage /> },
      { path: '/avisos/:id', element: <AnnouncementDetailPage /> },
      { path: '/avisos/:id/editar', element: <AnnouncementFormPage /> },
      { path: '/informacoes', element: <InfoPage /> },
      { path: '/informacoes/gerenciar', element: <InfoAdminPage /> },
      { path: '/faq', element: <FaqPage /> },
      { path: '/faq/gerenciar', element: <FaqAdminPage /> },
      { path: '/admin/acessos', element: <AccessManagementPage /> },
      { path: '/minha-unidade/moradores', element: <MyUnitMembersPage /> },
      { path: '/vagas/aluguel', element: <ParkingRentalsListPage /> },
      { path: '/vagas/aluguel/novo', element: <ParkingRentalFormPage /> },
      { path: '/vagas/aluguel/:id', element: <ParkingRentalDetailPage /> },
      { path: '/vagas/aluguel/:id/editar', element: <ParkingRentalFormPage /> },
      { path: '/documentos', element: <DocumentsPage /> },
    ],
  },
]);

export function AppRouter() {
  return <RouterProvider router={router} />;
}

import { createBrowserRouter, RouterProvider } from 'react-router-dom';
import { LoginPage } from '@/features/auth/pages/LoginPage';
import { RegisterMasterPage } from '@/features/auth/pages/RegisterMasterPage';
import { RegisterOwnerPage } from '@/features/auth/pages/RegisterOwnerPage';
import { PendingApprovalPage } from '@/features/auth/pages/PendingApprovalPage';
import { ForgotPasswordPage } from '@/features/auth/pages/ForgotPasswordPage';
import { ResetPasswordPage } from '@/features/auth/pages/ResetPasswordPage';
import { PrivacyPage } from '@/features/privacy/pages/PrivacyPage';
import { AboutPage } from '@/features/about/pages/AboutPage';
import { ProtectedRoute } from '@/components/ProtectedRoute';
import { Shell } from '@/components/layout/Shell';
import { PublicShell } from '@/components/layout/PublicShell';
import { PendingRegistrationsPage } from '@/features/admin/pages/PendingRegistrationsPage';
import { OwnershipClaimsPage } from '@/features/admin/pages/OwnershipClaimsPage';
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
import { RegisterExtraUnitPage } from '@/features/units/pages/RegisterExtraUnitPage';
import { ParkingRentalsListPage } from '@/features/parking-rentals/pages/ParkingRentalsListPage';
import { ParkingRentalDetailPage } from '@/features/parking-rentals/pages/ParkingRentalDetailPage';
import { ParkingRentalFormPage } from '@/features/parking-rentals/pages/ParkingRentalFormPage';
import { DocumentsPage } from '@/features/documents/pages/DocumentsPage';
import App from './App';

const router = createBrowserRouter([
  {
    // Casca pública: aviso de app independente (LGPD) fixo no topo das telas sem login.
    element: <PublicShell />,
    children: [
      { path: '/sobre', element: <AboutPage /> },
      { path: '/login', element: <LoginPage /> },
      { path: '/register-master', element: <RegisterMasterPage /> },
      { path: '/register-owner', element: <RegisterOwnerPage /> },
      { path: '/pending-approval', element: <PendingApprovalPage /> },
      { path: '/forgot-password', element: <ForgotPasswordPage /> },
      { path: '/reset', element: <ResetPasswordPage /> },
    ],
  },
  {
    // Leitura do conteudo do condominio: aberta a visitante (decisao do controlador dos dados,
    // 2026-08-25). Mesma casca do app; o Shell se vira sem usuario e oferece "Entrar".
    element: <Shell />,
    children: [
      { path: '/', element: <App /> },
      { path: '/informacoes', element: <InfoPage /> },
      { path: '/indicacoes', element: <RecommendationsListPage /> },
      { path: '/indicacoes/:id', element: <RecommendationDetailPage /> },
      { path: '/classificados', element: <ClassifiedsListPage /> },
      { path: '/classificados/:id', element: <ClassifiedDetailPage /> },
      { path: '/privacidade', element: <PrivacyPage /> },
    ],
  },
  {
    // Casca autenticada: escrita, area da unidade, admin e dados pessoais.
    element: (
      <ProtectedRoute>
        <Shell />
      </ProtectedRoute>
    ),
    children: [
      { path: '/avisos', element: <AnnouncementsListPage /> },
      { path: '/avisos/:id', element: <AnnouncementDetailPage /> },
      { path: '/faq', element: <FaqPage /> },
      { path: '/documentos', element: <DocumentsPage /> },
      { path: '/admin/registrations', element: <PendingRegistrationsPage /> },
      { path: '/admin/ownership-claims', element: <OwnershipClaimsPage /> },
      { path: '/admin/acessos', element: <AccessManagementPage /> },
      { path: '/classificados/novo', element: <ClassifiedFormPage /> },
      { path: '/classificados/:id/editar', element: <ClassifiedFormPage /> },
      { path: '/indicacoes/nova', element: <RecommendationFormPage /> },
      { path: '/indicacoes/:id/editar', element: <RecommendationFormPage /> },
      { path: '/avisos/novo', element: <AnnouncementFormPage /> },
      { path: '/avisos/:id/editar', element: <AnnouncementFormPage /> },
      { path: '/informacoes/gerenciar', element: <InfoAdminPage /> },
      { path: '/faq/gerenciar', element: <FaqAdminPage /> },
      { path: '/minha-unidade/moradores', element: <MyUnitMembersPage /> },
      { path: '/minha-unidade/registrar', element: <RegisterExtraUnitPage /> },
      { path: '/vagas/aluguel', element: <ParkingRentalsListPage /> },
      { path: '/vagas/aluguel/novo', element: <ParkingRentalFormPage /> },
      { path: '/vagas/aluguel/:id', element: <ParkingRentalDetailPage /> },
      { path: '/vagas/aluguel/:id/editar', element: <ParkingRentalFormPage /> },
    ],
  },
]);

export function AppRouter() {
  return <RouterProvider router={router} />;
}

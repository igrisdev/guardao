import { CalendarDays, LayoutDashboard, Settings, type LucideIcon } from "lucide-react";

export interface DashboardNavItem {
  label: string;
  href: string;
  icon: LucideIcon;
}

/**
 * Unica fuente de verdad de la navegacion del Sidebar del dashboard.
 *
 * "Luna IA" y "Conversaciones" se descartaron del alcance del producto
 * (ver docs/adr/009-sin-ia-en-el-producto.md) y no se agregan aqui a
 * proposito: el Sidebar solo lista lo que realmente forma parte del
 * dashboard hoy (Inicio) o en el corto plazo del roadmap (Agenda en la
 * Etapa 3, Config en la Etapa 2 — docs/plan-proyecto-guardao.md).
 */
export const dashboardNavItems: DashboardNavItem[] = [
  { label: "Inicio", href: "/dashboard", icon: LayoutDashboard },
  { label: "Agenda", href: "/dashboard/agenda", icon: CalendarDays },
  { label: "Config", href: "/dashboard/config", icon: Settings },
];

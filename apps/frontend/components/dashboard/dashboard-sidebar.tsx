"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { UserRound } from "lucide-react";

import { getCurrentSession } from "@/lib/api";
import { BrandMark } from "@/components/brand-mark";
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarSeparator,
} from "@/components/ui/sidebar";
import { LogoutButton } from "./logout-button";
import { dashboardNavItems } from "./nav-items";

function isNavItemActive(pathname: string, href: string): boolean {
  // "/dashboard" solo esta activo en la raiz exacta; si no, siempre
  // aparaceria resaltado sin importar la subruta.
  return href === "/dashboard" ? pathname === href : pathname.startsWith(href);
}

const ROLE_LABELS: Record<string, string> = {
  OWNER: "Dueño",
  STAFF: "Staff",
};

export function DashboardSidebar() {
  const pathname = usePathname();
  // DashboardSidebar solo monta una vez RequireSession confirma la sesion en
  // el cliente (ver components/dashboard/require-session.tsx), asi que para
  // cuando este componente se renderiza la sesion ya existe en localStorage.
  const session = getCurrentSession();
  const roleLabel = session ? (ROLE_LABELS[session.role] ?? session.role) : null;

  return (
    <Sidebar>
      <SidebarHeader>
        <div className="px-2 py-1.5">
          <BrandMark subtitle={session?.businessSlug ?? undefined} />
        </div>
      </SidebarHeader>
      <SidebarSeparator />
      <SidebarContent>
        <SidebarGroup>
          <SidebarGroupLabel>Panel</SidebarGroupLabel>
          <SidebarGroupContent>
            <SidebarMenu>
              {dashboardNavItems.map((item) => (
                <SidebarMenuItem key={item.href}>
                  <SidebarMenuButton
                    isActive={isNavItemActive(pathname, item.href)}
                    render={<Link href={item.href} />}
                  >
                    <item.icon />
                    <span>{item.label}</span>
                  </SidebarMenuButton>
                </SidebarMenuItem>
              ))}
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>
      </SidebarContent>
      <SidebarFooter>
        {roleLabel ? (
          <div className="flex items-center gap-2.5 px-2 py-1.5">
            <span className="flex size-7 shrink-0 items-center justify-center rounded-full bg-sidebar-accent text-sidebar-accent-foreground">
              <UserRound className="size-3.5" />
            </span>
            <span className="text-xs font-medium text-sidebar-foreground/80">{roleLabel}</span>
          </div>
        ) : null}
        <SidebarMenu>
          <SidebarMenuItem>
            <LogoutButton />
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarFooter>
    </Sidebar>
  );
}

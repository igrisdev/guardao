export default function DashboardAgendaPage() {
  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Agenda</h1>
        <p className="text-sm text-muted-foreground">
          Vista de citas del negocio (dia / semana / mes). Se conecta a la API en la Etapa 3
          del plan del proyecto.
        </p>
      </div>
      <div className="rounded-lg border border-dashed border-border p-10 text-center text-sm text-muted-foreground">
        Segunda pagina de ejemplo: confirma que el Sidebar navega correctamente entre rutas
        del dashboard.
      </div>
    </div>
  );
}

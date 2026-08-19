export default function GestionarReservaPage() {
  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Gestiona tu cita</h1>
        <p className="text-sm text-muted-foreground">
          Con el enlace privado que recibiste puedes confirmar tu asistencia, cancelar o
          reprogramar sin iniciar sesion.
        </p>
      </div>
      <div className="rounded-lg border border-dashed border-border p-10 text-center text-sm text-muted-foreground">
        Segunda pagina de ejemplo: confirma que la seccion publica navega sin depender del
        Sidebar del dashboard.
      </div>
    </div>
  );
}

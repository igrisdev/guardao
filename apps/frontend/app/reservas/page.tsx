import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";

export default function ReservasPage() {
  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Reserva tu cita</h1>
        <p className="text-sm text-muted-foreground">
          Elige un servicio y un horario disponible. No hace falta iniciar sesion.
        </p>
      </div>
      <Card>
        <CardHeader>
          <CardTitle>Servicios</CardTitle>
          <CardDescription>El calendario de disponibilidad se conecta aqui.</CardDescription>
        </CardHeader>
        <CardContent className="text-sm text-muted-foreground">
          Pagina de ejemplo dentro de BookingLayout, sin Sidebar del dashboard.
        </CardContent>
      </Card>
    </div>
  );
}

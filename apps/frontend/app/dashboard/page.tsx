import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";

export default function DashboardHomePage() {
  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Inicio</h1>
        <p className="text-sm text-muted-foreground">
          Resumen general del negocio. Aqui vivira el resumen del dia (citas, ingresos,
          ocupacion) cuando se conecte a datos reales.
        </p>
      </div>
      <Card>
        <CardHeader>
          <CardTitle>Bienvenido </CardTitle>
          <CardDescription>
            Usa el Sidebar para moverte entre las secciones del panel.
          </CardDescription>
        </CardHeader>
        <CardContent className="text-sm text-muted-foreground">
          Esta es la pagina de ejemplo que demuestra DashboardLayout.
        </CardContent>
      </Card>
    </div>
  );
}

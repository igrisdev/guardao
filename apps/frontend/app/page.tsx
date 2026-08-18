import { Button } from "@/components/ui/button";
import {
  Card,
  CardHeader,
  CardTitle,
  CardDescription,
  CardContent,
  CardFooter,
} from "@/components/ui/card";
import { Input } from "@/components/ui/input";

export default function Home() {
  return (
    <div className="flex flex-1 items-center justify-center bg-background p-8">
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle>Verificación de shadcn/ui</CardTitle>
          <CardDescription>
            Componentes base sobre la paleta oscura del producto.
          </CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          <div className="flex flex-col gap-1.5">
            <label htmlFor="demo-name" className="text-sm text-muted-foreground">
              Nombre
            </label>
            <Input id="demo-name" placeholder="Carlos" />
          </div>
          <div className="flex gap-2">
            <Button variant="secondary">Cancelar</Button>
            <Button>Guardar cambios</Button>
          </div>
        </CardContent>
        <CardFooter>
          <Button variant="destructive" size="sm">
            Cancelar cita
          </Button>
        </CardFooter>
      </Card>
    </div>
  );
}

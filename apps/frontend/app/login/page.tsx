"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Loader2, Lock } from "lucide-react";

import { HttpError, login } from "@/lib/api";
import {
  initialLoginFormValues,
  validateLoginForm,
  type LoginFormErrors,
  type LoginFormValues,
} from "@/lib/validation/login-form";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

function FormField({
  id,
  label,
  error,
  children,
}: {
  id: string;
  label: string;
  error?: string;
  children: React.ReactNode;
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <Label htmlFor={id}>{label}</Label>
      {children}
      {error ? <p className="text-xs text-destructive">{error}</p> : null}
    </div>
  );
}

export default function LoginPage() {
  const router = useRouter();

  const [values, setValues] = useState<LoginFormValues>(initialLoginFormValues);
  const [errors, setErrors] = useState<LoginFormErrors>({});
  const [generalError, setGeneralError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function updateField<K extends keyof LoginFormValues>(field: K, value: LoginFormValues[K]) {
    setValues((prev) => ({ ...prev, [field]: value }));
    setErrors((prev) => {
      if (!prev[field]) return prev;
      const next = { ...prev };
      delete next[field];
      return next;
    });
  }

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;

    const validationErrors = validateLoginForm(values);
    if (Object.keys(validationErrors).length > 0) {
      setErrors(validationErrors);
      setGeneralError(null);
      return;
    }

    setErrors({});
    setGeneralError(null);
    setSubmitting(true);
    try {
      await login({ email: values.email.trim(), password: values.password });
      // Un solo login para OWNER y STAFF: el rol viaja en el token y es el
      // dashboard el que decide, mas adelante, que le muestra a cada quien.
      router.replace("/dashboard");
    } catch (err) {
      if (err instanceof HttpError) {
        // INVALID_CREDENTIALS y cualquier otro codigo se muestran igual: el
        // backend ya devuelve un mensaje generico y seguro para el usuario
        // ("Correo o contraseña incorrectos"), sin distinguir cual de los dos
        // campos esta mal (evita confirmarle a un atacante que un correo
        // existe en la plataforma).
        setGeneralError(err.message);
      } else {
        setGeneralError("Ocurrió un error inesperado. Intenta de nuevo.");
      }
      setSubmitting(false);
    }
  }

  return (
    <Card className="w-full">
      <CardHeader>
        <span className="mb-2 flex size-11 items-center justify-center rounded-xl bg-accent text-accent-foreground">
          <Lock className="size-5" />
        </span>
        <CardTitle className="text-xl">Inicia sesión</CardTitle>
        <CardDescription>Entra con tu correo y contraseña.</CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-6">
          {generalError ? (
            <Alert variant="destructive">
              <AlertDescription>{generalError}</AlertDescription>
            </Alert>
          ) : null}

          <fieldset disabled={submitting} className="flex flex-col gap-4">
            <FormField id="email" label="Correo" error={errors.email}>
              <Input
                id="email"
                type="email"
                autoComplete="email"
                placeholder="dueno@barberia.co"
                value={values.email}
                aria-invalid={Boolean(errors.email)}
                onChange={(event) => updateField("email", event.target.value)}
              />
            </FormField>
            <FormField id="password" label="Contraseña" error={errors.password}>
              <Input
                id="password"
                type="password"
                autoComplete="current-password"
                value={values.password}
                aria-invalid={Boolean(errors.password)}
                onChange={(event) => updateField("password", event.target.value)}
              />
            </FormField>

            <Button type="submit" disabled={submitting} className="w-full" size="lg">
              {submitting ? (
                <>
                  <Loader2 className="animate-spin" />
                  Iniciando sesión...
                </>
              ) : (
                "Iniciar sesión"
              )}
            </Button>
          </fieldset>
        </form>
      </CardContent>
    </Card>
  );
}

"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Loader2 } from "lucide-react";

import { HttpError, register } from "@/lib/api";
import {
  initialRegisterFormValues,
  slugify,
  validateRegisterForm,
  type RegisterFormErrors,
  type RegisterFormValues,
} from "@/lib/validation/register-form";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Separator } from "@/components/ui/separator";

function FormField({
  id,
  label,
  error,
  hint,
  children,
}: {
  id: string;
  label: string;
  error?: string;
  hint?: string;
  children: React.ReactNode;
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <Label htmlFor={id}>{label}</Label>
      {children}
      {error ? (
        <p className="text-xs text-destructive">{error}</p>
      ) : hint ? (
        <p className="text-xs text-muted-foreground">{hint}</p>
      ) : null}
    </div>
  );
}

export default function RegisterPage() {
  const router = useRouter();

  const [values, setValues] = useState<RegisterFormValues>(initialRegisterFormValues);
  const [errors, setErrors] = useState<RegisterFormErrors>({});
  const [slugTouched, setSlugTouched] = useState(false);
  const [generalError, setGeneralError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function updateField<K extends keyof RegisterFormValues>(field: K, value: RegisterFormValues[K]) {
    setValues((prev) => ({ ...prev, [field]: value }));
    setErrors((prev) => {
      if (!prev[field]) return prev;
      const next = { ...prev };
      delete next[field];
      return next;
    });
  }

  function handleBusinessNameChange(value: string) {
    updateField("businessName", value);
    // Sugerencia automatica mientras el usuario no haya tocado el slug a
    // mano: en cuanto lo edita, se respeta lo que haya escrito.
    if (!slugTouched) {
      updateField("slug", slugify(value));
    }
  }

  function handleSlugChange(value: string) {
    setSlugTouched(true);
    updateField("slug", value);
  }

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;

    const validationErrors = validateRegisterForm(values);
    if (Object.keys(validationErrors).length > 0) {
      setErrors(validationErrors);
      setGeneralError(null);
      return;
    }

    setErrors({});
    setGeneralError(null);
    setSubmitting(true);
    try {
      await register({
        businessName: values.businessName.trim(),
        slug: values.slug.trim(),
        locationName: values.locationName.trim(),
        address: values.address.trim() || undefined,
        city: values.city.trim() || undefined,
        email: values.email.trim(),
        password: values.password,
      });
      // register() ya guardo la sesion (tokenStorage) antes de resolver: el
      // dashboard se abre con el usuario autenticado, sin pasar por /login.
      router.replace("/dashboard");
    } catch (err) {
      if (err instanceof HttpError) {
        if (err.code === "SLUG_TAKEN") {
          setErrors((prev) => ({ ...prev, slug: "Este slug ya está registrado. Elige otro." }));
        } else if (err.code === "EMAIL_TAKEN") {
          setErrors((prev) => ({
            ...prev,
            email: "Este correo ya está registrado. Intenta con otro correo.",
          }));
        } else if (err.code === "VALIDATION_ERROR") {
          const fields = (err.details?.fields as Record<string, string> | undefined) ?? {};
          if (Object.keys(fields).length > 0) {
            setErrors((prev) => ({ ...prev, ...fields }));
          } else {
            setGeneralError(err.message);
          }
        } else {
          setGeneralError(err.message);
        }
      } else {
        setGeneralError("Ocurrió un error inesperado. Intenta de nuevo.");
      }
      setSubmitting(false);
    }
  }

  return (
    <Card className="w-full">
      <CardHeader>
        <CardTitle>Registra tu barbería</CardTitle>
        <CardDescription>
          Crea el negocio, su primera sede y tu cuenta de dueño. Al terminar quedas con la sesión
          iniciada.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-6">
          {generalError ? (
            <Alert variant="destructive">
              <AlertDescription>{generalError}</AlertDescription>
            </Alert>
          ) : null}

          <fieldset disabled={submitting} className="flex flex-col gap-6">
            <div className="flex flex-col gap-4">
              <h2 className="text-sm font-semibold text-foreground">Datos del negocio</h2>
              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                <FormField id="businessName" label="Nombre de la barbería" error={errors.businessName}>
                  <Input
                    id="businessName"
                    autoComplete="organization"
                    placeholder="Barbería El Corte"
                    value={values.businessName}
                    aria-invalid={Boolean(errors.businessName)}
                    onChange={(event) => handleBusinessNameChange(event.target.value)}
                  />
                </FormField>
                <FormField
                  id="slug"
                  label="URL pública"
                  error={errors.slug}
                  hint={!errors.slug ? "guardao.com/book/" + (values.slug || "tu-barberia") : undefined}
                >
                  <Input
                    id="slug"
                    autoComplete="off"
                    placeholder="barberia-el-corte"
                    value={values.slug}
                    aria-invalid={Boolean(errors.slug)}
                    onChange={(event) => handleSlugChange(event.target.value)}
                  />
                </FormField>
              </div>
            </div>

            <Separator />

            <div className="flex flex-col gap-4">
              <h2 className="text-sm font-semibold text-foreground">Datos de la primera sede</h2>
              <FormField id="locationName" label="Nombre de la sede" error={errors.locationName}>
                <Input
                  id="locationName"
                  autoComplete="off"
                  placeholder="Sede Centro"
                  value={values.locationName}
                  aria-invalid={Boolean(errors.locationName)}
                  onChange={(event) => updateField("locationName", event.target.value)}
                />
              </FormField>
              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                <FormField id="address" label="Dirección (opcional)" error={errors.address}>
                  <Input
                    id="address"
                    autoComplete="street-address"
                    placeholder="Calle 10 # 5-20"
                    value={values.address}
                    aria-invalid={Boolean(errors.address)}
                    onChange={(event) => updateField("address", event.target.value)}
                  />
                </FormField>
                <FormField id="city" label="Ciudad (opcional)" error={errors.city}>
                  <Input
                    id="city"
                    autoComplete="address-level2"
                    placeholder="Cali"
                    value={values.city}
                    aria-invalid={Boolean(errors.city)}
                    onChange={(event) => updateField("city", event.target.value)}
                  />
                </FormField>
              </div>
            </div>

            <Separator />

            <div className="flex flex-col gap-4">
              <h2 className="text-sm font-semibold text-foreground">Datos del dueño</h2>
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
              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                <FormField
                  id="password"
                  label="Contraseña"
                  error={errors.password}
                  hint={!errors.password ? "Entre 8 y 72 caracteres" : undefined}
                >
                  <Input
                    id="password"
                    type="password"
                    autoComplete="new-password"
                    value={values.password}
                    aria-invalid={Boolean(errors.password)}
                    onChange={(event) => updateField("password", event.target.value)}
                  />
                </FormField>
                <FormField id="confirmPassword" label="Confirmar contraseña" error={errors.confirmPassword}>
                  <Input
                    id="confirmPassword"
                    type="password"
                    autoComplete="new-password"
                    value={values.confirmPassword}
                    aria-invalid={Boolean(errors.confirmPassword)}
                    onChange={(event) => updateField("confirmPassword", event.target.value)}
                  />
                </FormField>
              </div>
            </div>

            <Button type="submit" disabled={submitting} className="w-full" size="lg">
              {submitting ? (
                <>
                  <Loader2 className="animate-spin" />
                  Creando tu barbería...
                </>
              ) : (
                "Crear mi barbería"
              )}
            </Button>
          </fieldset>
        </form>
      </CardContent>
    </Card>
  );
}

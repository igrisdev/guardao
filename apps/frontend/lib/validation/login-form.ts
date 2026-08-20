/**
 * Validacion en cliente del formulario de login.
 *
 * Mas simple que register-form.ts a proposito: aqui no se esta creando una
 * cuenta, solo verificando una que ya existe, asi que no aplican las reglas
 * de longitud/formato de esos campos (Etapa 1 del plan, GUA-27). El backend
 * ya responde igual ante "no existe ese correo" y "la clave esta mal"
 * (INVALID_CREDENTIALS), asi que tampoco hace falta separar esos dos casos
 * aqui: solo se valida que ambos campos vengan llenos.
 */

export interface LoginFormValues {
  email: string;
  password: string;
}

export type LoginFormErrors = Partial<Record<keyof LoginFormValues, string>>;

export const initialLoginFormValues: LoginFormValues = {
  email: "",
  password: "",
};

export function validateLoginForm(values: LoginFormValues): LoginFormErrors {
  const errors: LoginFormErrors = {};

  if (!values.email.trim()) {
    errors.email = "El correo es obligatorio";
  }

  if (!values.password) {
    errors.password = "La contraseña es obligatoria";
  }

  return errors;
}

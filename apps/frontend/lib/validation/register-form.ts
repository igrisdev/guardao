/**
 * Validacion en cliente del formulario de registro de barberia.
 *
 * Los mensajes y los limites replican exactamente las reglas del backend
 * (RegisterRequest.java, @NotBlank/@Size/@Pattern) para que el usuario no
 * vea una regla en el cliente y otra distinta al llegar al servidor. Si esas
 * reglas cambian en el backend, deben cambiar aqui tambien.
 */

export interface RegisterFormValues {
  businessName: string;
  slug: string;
  locationName: string;
  address: string;
  city: string;
  email: string;
  password: string;
  confirmPassword: string;
}

export type RegisterFormErrors = Partial<Record<keyof RegisterFormValues, string>>;

const SLUG_PATTERN = /^[a-z0-9]+(-[a-z0-9]+)*$/;
// Chequeo simple y permisivo, igual de estricto que @Email de Hibernate
// Validator: solo busca la forma general del correo, la validacion real de
// que exista es cosa del backend (y de mandarle un correo de verdad).
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export const initialRegisterFormValues: RegisterFormValues = {
  businessName: "",
  slug: "",
  locationName: "",
  address: "",
  city: "",
  email: "",
  password: "",
  confirmPassword: "",
};

export function validateRegisterForm(values: RegisterFormValues): RegisterFormErrors {
  const errors: RegisterFormErrors = {};

  const businessName = values.businessName.trim();
  if (!businessName) {
    errors.businessName = "El nombre del negocio es obligatorio";
  } else if (businessName.length > 120) {
    errors.businessName = "El nombre no puede pasar de 120 caracteres";
  }

  const slug = values.slug.trim();
  if (!slug) {
    errors.slug = "La URL publica es obligatoria";
  } else if (slug.length > 80) {
    errors.slug = "La URL publica no puede pasar de 80 caracteres";
  } else if (!SLUG_PATTERN.test(slug)) {
    errors.slug = "Use solo minusculas, numeros y guiones (ejemplo: barberia-el-corte)";
  }

  const locationName = values.locationName.trim();
  if (!locationName) {
    errors.locationName = "El nombre de la sede es obligatorio";
  } else if (locationName.length > 120) {
    errors.locationName = "El nombre de la sede no puede pasar de 120 caracteres";
  }

  if (values.address.trim().length > 200) {
    errors.address = "La direccion no puede pasar de 200 caracteres";
  }

  if (values.city.trim().length > 80) {
    errors.city = "La ciudad no puede pasar de 80 caracteres";
  }

  const email = values.email.trim();
  if (!email) {
    errors.email = "El correo es obligatorio";
  } else if (email.length > 180) {
    errors.email = "El correo no puede pasar de 180 caracteres";
  } else if (!EMAIL_PATTERN.test(email)) {
    errors.email = "El correo no tiene un formato valido";
  }

  if (!values.password) {
    errors.password = "La contraseña es obligatoria";
  } else if (values.password.length < 8 || values.password.length > 72) {
    errors.password = "La contraseña debe tener entre 8 y 72 caracteres";
  }

  if (!values.confirmPassword) {
    errors.confirmPassword = "Confirma la contraseña";
  } else if (values.confirmPassword !== values.password) {
    errors.confirmPassword = "Las contraseñas no coinciden";
  }

  return errors;
}

/** Sugerencia de slug a partir del nombre del negocio; el usuario puede editarla libremente despues. */
export function slugify(value: string): string {
  return value
    .toLowerCase()
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

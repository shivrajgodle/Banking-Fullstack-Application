import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators, AbstractControl, ValidationErrors } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

// HTML uses: success(), errorMsg(), loading(), form, isInvalid(), onSubmit()
// Form fields: firstName, lastName, email, phoneNumber, dateOfBirth, nationalId, city, state, password, confirmPassword

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './register.component.html',
  styleUrl: './register.component.scss'
})
export class RegisterComponent {
  private fb          = inject(FormBuilder);
  private authService = inject(AuthService);
  private router      = inject(Router);

  loading  = signal(false);
  errorMsg = signal('');
  success  = signal(false); // ✅ HTML calls success()

  form = this.fb.group({
    firstName:       ['', Validators.required],
    lastName:        ['', Validators.required],
    email:           ['', [Validators.required, Validators.email]],
    phoneNumber:     ['', [Validators.required, Validators.pattern(/^\d{10}$/)]], // ✅ HTML uses phoneNumber
    dateOfBirth:     ['', Validators.required],
    nationalId:      ['', [Validators.required, Validators.pattern(/^\d{12}$/)]], // 12-digit Aadhaar
    city:            [''],
    state:           [''],
    password:        ['', [Validators.required, Validators.minLength(8)]],
    confirmPassword: ['', Validators.required]
  }, { validators: this.passwordsMatch });

  private passwordsMatch(group: AbstractControl): ValidationErrors | null {
    const pwd  = group.get('password')?.value;
    const conf = group.get('confirmPassword')?.value;
    return pwd && conf && pwd !== conf ? { passwordMismatch: true } : null;
  }

  isInvalid(field: string): boolean {
    const c = this.form.get(field);
    if (c?.invalid && c?.touched) return true;
    // Special case: confirmPassword also invalid when passwords don't match
    if (field === 'confirmPassword' && this.form.hasError('passwordMismatch') && c?.touched) return true;
    return false;
  }

  onSubmit(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.loading.set(true);
    this.errorMsg.set('');
    this.authService.register(this.form.value as any).subscribe({
      next: () => {
        this.loading.set(false);
        this.success.set(true); // Show success banner, hide form
        setTimeout(() => this.router.navigateByUrl('/login'), 2000);
      },
      error: err => {
        this.loading.set(false);
        this.errorMsg.set(err?.error?.message || 'Registration failed. Please try again.');
      }
    });
  }
}

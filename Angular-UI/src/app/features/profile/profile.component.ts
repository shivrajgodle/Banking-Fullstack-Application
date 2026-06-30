/**
 * ProfileComponent — User profile, address, and password management.
 *
 * Root Causes Fixed:
 *  1. Signal names mismatched HTML: saving→savingProfile, changing→changingPwd,
 *     passwordForm→pwdForm, and pwdError() signal was missing entirely.
 *  2. Form fields mismatched UserResponse model: 'phone' field doesn't exist on
 *     UserResponse (it uses 'phoneNumber'); also city/state/zipCode/country were
 *     missing from the form but present in the HTML template.
 *  3. currentUser$ BehaviorSubject emits the localStorage value at startup, but
 *     if the stored user object is stale or missing, the form stays empty.
 *     Fix: always call getCurrentUser() on init to re-fetch fresh data from the
 *     backend and push it into the BehaviorSubject — this also refreshes the
 *     topbar/sidebar which subscribe to the same stream.
 */
import { Component, OnInit, inject, signal } from "@angular/core";
import { CommonModule, TitleCasePipe, DatePipe } from "@angular/common";
import { ReactiveFormsModule, FormBuilder, Validators } from "@angular/forms";
import { UserService } from "../../core/services/user.service";
import { AuthService } from "../../core/services/auth.service";
import { ToastService } from "../../core/services/toast.service";
import { UserResponse } from "../../shared/models";

@Component({
  selector: "app-profile",
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, TitleCasePipe, DatePipe],
  templateUrl: "./profile.component.html",
  styleUrl: "./profile.component.scss",
})
export class ProfileComponent implements OnInit {
  private userService = inject(UserService);
  private authService = inject(AuthService);
  private toastService = inject(ToastService);
  private fb = inject(FormBuilder);

  user = signal<UserResponse | null>(null);
  // ⚠️ FIX: Signal names must match what the HTML template calls
  savingProfile = signal(false);
  changingPwd = signal(false);
  pwdError = signal(""); // ⚠️ FIX: was missing, HTML calls pwdError()

  // ⚠️ FIX: Form fields now match UserResponse model fields exactly.
  //   Old code had 'phone' — model field is 'phoneNumber' (read-only, not editable).
  //   Added city, state, zipCode, country which were in HTML but missing from form.
  profileForm = this.fb.group({
    firstName: ["", Validators.required],
    lastName: ["", Validators.required],
    address: [""],
    city: [""],
    state: [""],
    zipCode: [""],
    country: [""],
  });

  // ⚠️ FIX: Variable name must match HTML — HTML uses pwdForm, old code used passwordForm
  pwdForm = this.fb.group({
    currentPassword: ["", Validators.required],
    newPassword: ["", [Validators.required, Validators.minLength(8)]],
    confirmPassword: ["", Validators.required],
  });

  ngOnInit(): void {
    // ⚠️ FIX: Always fetch fresh user data from backend on init.
    //   The BehaviorSubject is seeded from localStorage, which can be stale or
    //   missing after a hard refresh. This call re-fetches from /auth/me and
    //   pushes the result into currentUser$, which updates the topbar/sidebar too.
    this.authService.getCurrentUser().subscribe({
      error: () => {
        // Fallback: use whatever is already in the BehaviorSubject (localStorage)
      },
    });

    // Subscribe to the live user stream — this handles both the immediate
    // localStorage value AND the updated value from getCurrentUser() above.
    this.authService.currentUser$.subscribe((u) => {
      this.user.set(u);
      if (u) {
        this.profileForm.patchValue({
          firstName: u.firstName ?? "",
          lastName: u.lastName ?? "",
          address: u.address ?? "",
          city: u.city ?? "",
          state: u.state ?? "",
          zipCode: u.zipCode ?? "",
          country: u.country ?? "",
        });
      }
    });
  }

  saveProfile(): void {
    if (this.profileForm.invalid) {
      this.profileForm.markAllAsTouched();
      return;
    }
    this.savingProfile.set(true);
    this.userService
      .updateProfile(this.profileForm.value as Partial<UserResponse>)
      .subscribe({
        next: (r) => {
          this.savingProfile.set(false);
          // Update the BehaviorSubject so topbar name refreshes immediately
          this.authService.getCurrentUser().subscribe();
          this.toastService.success("Saved", "Profile updated successfully.");
        },
        error: (err) => {
          this.savingProfile.set(false);
          this.toastService.error(
            "Failed",
            err?.error?.message || "Could not update profile.",
          );
        },
      });
  }

  changePassword(): void {
    this.pwdError.set("");
    if (this.pwdForm.invalid) {
      this.pwdForm.markAllAsTouched();
      return;
    }
    const v = this.pwdForm.value;
    if (v.newPassword !== v.confirmPassword) {
      this.pwdError.set("New passwords do not match.");
      return;
    }
    this.changingPwd.set(true);
    this.userService
      .changePassword(v.currentPassword!, v.newPassword!, v.confirmPassword!)
      .subscribe({
        next: () => {
          this.changingPwd.set(false);
          this.pwdForm.reset();
          this.toastService.success(
            "Changed",
            "Password changed successfully.",
          );
        },
        error: (err) => {
          this.changingPwd.set(false);
          this.pwdError.set(
            err?.error?.message || "Could not change password.",
          );
        },
      });
  }

  logout(): void {
    this.authService.logout();
  }

  isInvalid(form: any, field: string): boolean {
    const c = form.get(field);
    return !!(c?.invalid && c?.touched);
  }

  getInitials(): string {
    const u = this.user();
    if (!u) return "?";
    return (u.firstName[0] + u.lastName[0]).toUpperCase();
  }

  /** Masks sensitive ID: shows last 4 chars, rest as '*' */
  maskId(id: string): string {
    if (!id || id.length <= 4) return id;
    return "*".repeat(id.length - 4) + id.slice(-4);
  }
}

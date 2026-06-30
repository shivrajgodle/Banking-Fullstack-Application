import { Component, OnInit, inject, signal } from "@angular/core";
import { CommonModule, DatePipe } from "@angular/common";
import { ReactiveFormsModule, FormBuilder, Validators } from "@angular/forms";
import { BeneficiaryService } from "../../core/services/beneficiary.service";
import { ToastService } from "../../core/services/toast.service";
import { BeneficiaryResponse } from "../../shared/models";

// HTML uses:
//   loading(), benes(), showAdd (signal with .set), addStep(), otpRequesting(),
//   adding(), addForm, startAdd(), requestOtp(), addBene(), delete(b)

@Component({
  selector: "app-beneficiaries",
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, DatePipe],
  templateUrl: "./beneficiaries.component.html",
  styleUrl: "./beneficiaries.component.scss",
})
export class BeneficiariesComponent implements OnInit {
  private beneficiaryService = inject(BeneficiaryService);
  private toastService = inject(ToastService);
  private fb = inject(FormBuilder);

  benes = signal<BeneficiaryResponse[]>([]);
  loading = signal(true);
  showAdd = signal(false); // ✅ used as showAdd() and showAdd.set(false)
  addStep = signal(1); // ✅ HTML: addStep() === 1 / 2
  otpRequesting = signal(false); // ✅ HTML: otpRequesting()
  adding = signal(false); // ✅ HTML: adding()

  addForm = this.fb.group({
    beneficiaryName: ["", Validators.required],
    accountNumber: ["", Validators.required],
    ifscCode: ["", Validators.required],
    bankName: [""],
    nickname: [""],
    otp: ["", [Validators.required, Validators.minLength(6)]],
  });

  ngOnInit(): void {
    this.loadBeneficiaries();
  }

  loadBeneficiaries(): void {
    this.beneficiaryService.getBeneficiaries().subscribe({
      next: (r) => {
        this.benes.set(r.data);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  startAdd(): void {
    this.showAdd.set(true);
    this.addStep.set(1); // ✅ always start at step 1 (OTP request)
    this.addForm.reset();
  }

  requestOtp(): void {
    // ✅ HTML calls requestOtp()
    this.otpRequesting.set(true);
    this.beneficiaryService.requestBeneficiaryOtp().subscribe({
      next: () => {
        this.otpRequesting.set(false);
        this.addStep.set(2); // Advance to form step
        this.toastService.info("OTP Sent", "Check your registered email.");
      },
      error: () => {
        this.otpRequesting.set(false);
        this.toastService.error("Error", "Could not send OTP.");
      },
    });
  }

  addBene(): void {
    // ✅ HTML calls addBene() (not addBeneficiary)
    if (this.addForm.invalid) {
      this.addForm.markAllAsTouched();
      return;
    }
    this.adding.set(true);
    const v = this.addForm.value;
    this.beneficiaryService
      .addBeneficiary({
        beneficiaryName: v.beneficiaryName!,
        accountNumber: v.accountNumber!,
        ifscCode: v.ifscCode!,
        bankName: v.bankName || undefined,
        nickname: v.nickname || undefined,
        otp: v.otp!,
      })
      .subscribe({
        next: (r) => {
          this.benes.update((list) => [...list, r.data]);
          this.showAdd.set(false);
          this.adding.set(false);
          this.toastService.success(
            "Added",
            `${r.data.beneficiaryName} added successfully.`,
          );
        },
        error: (err) => {
          this.adding.set(false);
          this.toastService.error(
            "Failed",
            err?.error?.message || "Could not add beneficiary.",
          );
        },
      });
  }

  delete(b: BeneficiaryResponse): void {
    if (!confirm(`Remove ${b.beneficiaryName} from beneficiaries?`)) return;
    this.beneficiaryService.deleteBeneficiary(b.id).subscribe({
      next: () => {
        this.benes.update((list) => list.filter((x) => x.id !== b.id));
        this.toastService.success("Removed", `${b.beneficiaryName} removed.`);
      },
      error: (err) =>
        this.toastService.error(
          "Failed",
          err?.error?.message || "Could not remove.",
        ),
    });
  }
}

resource "google_billing_budget" "zero_cost_guard" {
  billing_account = var.billing_account
  display_name    = "funandfree zero-cost guard"

  budget_filter {
    projects               = ["projects/${var.project_number}"]
    calendar_period        = "MONTH"
    credit_types_treatment = "INCLUDE_ALL_CREDITS"
  }

  amount {
    specified_amount {
      currency_code = var.budget_currency
      units         = var.budget_amount
    }
  }

  threshold_rules {
    threshold_percent = 0.25
    spend_basis       = "CURRENT_SPEND"
  }

  threshold_rules {
    threshold_percent = 0.5
    spend_basis       = "CURRENT_SPEND"
  }

  threshold_rules {
    threshold_percent = 1.0
    spend_basis       = "CURRENT_SPEND"
  }
}

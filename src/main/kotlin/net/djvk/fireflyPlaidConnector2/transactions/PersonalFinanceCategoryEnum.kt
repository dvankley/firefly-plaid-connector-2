package net.djvk.fireflyPlaidConnector2.transactions

import net.djvk.fireflyPlaidConnector2.api.plaid.models.PersonalFinanceCategory
import net.djvk.fireflyPlaidConnector2.constants.Direction

/**
 * Possible values of [PersonalFinanceCategory]
 * Pulled from https://plaid.com/documents/transactions-personal-finance-category-taxonomy.csv
 */
enum class PersonalFinanceCategoryEnum(val primary: Primary, val detailed: Detailed) {
    INCOME_DIVIDENDS(Primary.INCOME, IncomeDetailed.DIVIDENDS),
    INCOME_INTEREST_EARNED(Primary.INCOME, IncomeDetailed.INTEREST_EARNED),
    INCOME_RETIREMENT_PENSION(Primary.INCOME, IncomeDetailed.RETIREMENT_PENSION),
    INCOME_TAX_REFUND(Primary.INCOME, IncomeDetailed.TAX_REFUND),
    INCOME_UNEMPLOYMENT(Primary.INCOME, IncomeDetailed.UNEMPLOYMENT),
    INCOME_WAGES(Primary.INCOME, IncomeDetailed.WAGES),
    INCOME_OTHER_INCOME(Primary.INCOME, IncomeDetailed.OTHER_INCOME),
    TRANSFER_IN_CASH_ADVANCES_AND_LOANS(Primary.TRANSFER_IN, TransferInDetailed.CASH_ADVANCES_AND_LOANS),
    TRANSFER_IN_DEPOSIT(Primary.TRANSFER_IN, TransferInDetailed.DEPOSIT),
    TRANSFER_IN_INVESTMENT_AND_RETIREMENT_FUNDS(
        Primary.TRANSFER_IN,
        TransferInDetailed.INVESTMENT_AND_RETIREMENT_FUNDS
    ),
    TRANSFER_IN_SAVINGS(Primary.TRANSFER_IN, TransferInDetailed.SAVINGS),
    TRANSFER_IN_ACCOUNT_TRANSFER(Primary.TRANSFER_IN, TransferInDetailed.ACCOUNT_TRANSFER),
    TRANSFER_IN_OTHER_TRANSFER_IN(Primary.TRANSFER_IN, TransferInDetailed.OTHER_TRANSFER_IN),
    TRANSFER_OUT_INVESTMENT_AND_RETIREMENT_FUNDS(
        Primary.TRANSFER_OUT,
        TransferOutDetailed.INVESTMENT_AND_RETIREMENT_FUNDS
    ),
    TRANSFER_OUT_SAVINGS(Primary.TRANSFER_OUT, TransferOutDetailed.SAVINGS),
    TRANSFER_OUT_WITHDRAWAL(Primary.TRANSFER_OUT, TransferOutDetailed.WITHDRAWAL),
    TRANSFER_OUT_ACCOUNT_TRANSFER(Primary.TRANSFER_OUT, TransferOutDetailed.ACCOUNT_TRANSFER),
    TRANSFER_OUT_OTHER_TRANSFER_OUT(Primary.TRANSFER_OUT, TransferOutDetailed.OTHER_TRANSFER_OUT),
    LOAN_PAYMENTS_CAR_PAYMENT(Primary.LOAN_PAYMENTS, LoanPaymentsDetailed.CAR_PAYMENT),
    LOAN_PAYMENTS_CREDIT_CARD_PAYMENT(Primary.LOAN_PAYMENTS, LoanPaymentsDetailed.CREDIT_CARD_PAYMENT),
    LOAN_PAYMENTS_PERSONAL_LOAN_PAYMENT(Primary.LOAN_PAYMENTS, LoanPaymentsDetailed.PERSONAL_LOAN_PAYMENT),
    LOAN_PAYMENTS_MORTGAGE_PAYMENT(Primary.LOAN_PAYMENTS, LoanPaymentsDetailed.MORTGAGE_PAYMENT),
    LOAN_PAYMENTS_STUDENT_LOAN_PAYMENT(Primary.LOAN_PAYMENTS, LoanPaymentsDetailed.STUDENT_LOAN_PAYMENT),
    LOAN_PAYMENTS_OTHER_PAYMENT(Primary.LOAN_PAYMENTS, LoanPaymentsDetailed.OTHER_PAYMENT),
    BANK_FEES_ATM_FEES(Primary.BANK_FEES, BankFeesDetailed.ATM_FEES),
    BANK_FEES_FOREIGN_TRANSACTION_FEES(Primary.BANK_FEES, BankFeesDetailed.FOREIGN_TRANSACTION_FEES),
    BANK_FEES_INSUFFICIENT_FUNDS(Primary.BANK_FEES, BankFeesDetailed.INSUFFICIENT_FUNDS),
    BANK_FEES_INTEREST_CHARGE(Primary.BANK_FEES, BankFeesDetailed.INTEREST_CHARGE),
    BANK_FEES_OVERDRAFT_FEES(Primary.BANK_FEES, BankFeesDetailed.OVERDRAFT_FEES),
    BANK_FEES_OTHER_BANK_FEES(Primary.BANK_FEES, BankFeesDetailed.OTHER_BANK_FEES),
    ENTERTAINMENT_CASINOS_AND_GAMBLING(Primary.ENTERTAINMENT, EntertainmentDetailed.CASINOS_AND_GAMBLING),
    ENTERTAINMENT_MUSIC_AND_AUDIO(Primary.ENTERTAINMENT, EntertainmentDetailed.MUSIC_AND_AUDIO),
    ENTERTAINMENT_SPORTING_EVENTS_AMUSEMENT_PARKS_AND_MUSEUMS(
        Primary.ENTERTAINMENT,
        EntertainmentDetailed.SPORTING_EVENTS_AMUSEMENT_PARKS_AND_MUSEUMS
    ),
    ENTERTAINMENT_TV_AND_MOVIES(Primary.ENTERTAINMENT, EntertainmentDetailed.TV_AND_MOVIES),
    ENTERTAINMENT_VIDEO_GAMES(Primary.ENTERTAINMENT, EntertainmentDetailed.VIDEO_GAMES),
    ENTERTAINMENT_OTHER_ENTERTAINMENT(Primary.ENTERTAINMENT, EntertainmentDetailed.OTHER_ENTERTAINMENT),
    FOOD_AND_DRINK_BEER_WINE_AND_LIQUOR(Primary.FOOD_AND_DRINK, FoodAndDrinkDetailed.BEER_WINE_AND_LIQUOR),
    FOOD_AND_DRINK_COFFEE(Primary.FOOD_AND_DRINK, FoodAndDrinkDetailed.COFFEE),
    FOOD_AND_DRINK_FAST_FOOD(Primary.FOOD_AND_DRINK, FoodAndDrinkDetailed.FAST_FOOD),
    FOOD_AND_DRINK_GROCERIES(Primary.FOOD_AND_DRINK, FoodAndDrinkDetailed.GROCERIES),
    FOOD_AND_DRINK_RESTAURANT(Primary.FOOD_AND_DRINK, FoodAndDrinkDetailed.RESTAURANT),
    FOOD_AND_DRINK_VENDING_MACHINES(Primary.FOOD_AND_DRINK, FoodAndDrinkDetailed.VENDING_MACHINES),
    FOOD_AND_DRINK_OTHER_FOOD_AND_DRINK(Primary.FOOD_AND_DRINK, FoodAndDrinkDetailed.OTHER_FOOD_AND_DRINK),
    GENERAL_MERCHANDISE_BOOKSTORES_AND_NEWSSTANDS(
        Primary.GENERAL_MERCHANDISE,
        GeneralMerchandiseDetailed.BOOKSTORES_AND_NEWSSTANDS
    ),
    GENERAL_MERCHANDISE_CLOTHING_AND_ACCESSORIES(
        Primary.GENERAL_MERCHANDISE,
        GeneralMerchandiseDetailed.CLOTHING_AND_ACCESSORIES
    ),
    GENERAL_MERCHANDISE_CONVENIENCE_STORES(Primary.GENERAL_MERCHANDISE, GeneralMerchandiseDetailed.CONVENIENCE_STORES),
    GENERAL_MERCHANDISE_DEPARTMENT_STORES(Primary.GENERAL_MERCHANDISE, GeneralMerchandiseDetailed.DEPARTMENT_STORES),
    GENERAL_MERCHANDISE_DISCOUNT_STORES(Primary.GENERAL_MERCHANDISE, GeneralMerchandiseDetailed.DISCOUNT_STORES),
    GENERAL_MERCHANDISE_ELECTRONICS(Primary.GENERAL_MERCHANDISE, GeneralMerchandiseDetailed.ELECTRONICS),
    GENERAL_MERCHANDISE_GIFTS_AND_NOVELTIES(
        Primary.GENERAL_MERCHANDISE,
        GeneralMerchandiseDetailed.GIFTS_AND_NOVELTIES
    ),
    GENERAL_MERCHANDISE_OFFICE_SUPPLIES(Primary.GENERAL_MERCHANDISE, GeneralMerchandiseDetailed.OFFICE_SUPPLIES),
    GENERAL_MERCHANDISE_ONLINE_MARKETPLACES(
        Primary.GENERAL_MERCHANDISE,
        GeneralMerchandiseDetailed.ONLINE_MARKETPLACES
    ),
    GENERAL_MERCHANDISE_PET_SUPPLIES(Primary.GENERAL_MERCHANDISE, GeneralMerchandiseDetailed.PET_SUPPLIES),
    GENERAL_MERCHANDISE_SPORTING_GOODS(Primary.GENERAL_MERCHANDISE, GeneralMerchandiseDetailed.SPORTING_GOODS),
    GENERAL_MERCHANDISE_SUPERSTORES(Primary.GENERAL_MERCHANDISE, GeneralMerchandiseDetailed.SUPERSTORES),
    GENERAL_MERCHANDISE_TOBACCO_AND_VAPE(Primary.GENERAL_MERCHANDISE, GeneralMerchandiseDetailed.TOBACCO_AND_VAPE),
    GENERAL_MERCHANDISE_OTHER_GENERAL_MERCHANDISE(
        Primary.GENERAL_MERCHANDISE,
        GeneralMerchandiseDetailed.OTHER_GENERAL_MERCHANDISE
    ),
    HOME_IMPROVEMENT_FURNITURE(Primary.HOME_IMPROVEMENT, HomeImprovementDetailed.FURNITURE),
    HOME_IMPROVEMENT_HARDWARE(Primary.HOME_IMPROVEMENT, HomeImprovementDetailed.HARDWARE),
    HOME_IMPROVEMENT_REPAIR_AND_MAINTENANCE(Primary.HOME_IMPROVEMENT, HomeImprovementDetailed.REPAIR_AND_MAINTENANCE),
    HOME_IMPROVEMENT_SECURITY(Primary.HOME_IMPROVEMENT, HomeImprovementDetailed.SECURITY),
    HOME_IMPROVEMENT_OTHER_HOME_IMPROVEMENT(Primary.HOME_IMPROVEMENT, HomeImprovementDetailed.OTHER_HOME_IMPROVEMENT),
    MEDICAL_DENTAL_CARE(Primary.MEDICAL, MedicalDetailed.DENTAL_CARE),
    MEDICAL_EYE_CARE(Primary.MEDICAL, MedicalDetailed.EYE_CARE),
    MEDICAL_NURSING_CARE(Primary.MEDICAL, MedicalDetailed.NURSING_CARE),
    MEDICAL_PHARMACIES_AND_SUPPLEMENTS(Primary.MEDICAL, MedicalDetailed.PHARMACIES_AND_SUPPLEMENTS),
    MEDICAL_PRIMARY_CARE(Primary.MEDICAL, MedicalDetailed.PRIMARY_CARE),
    MEDICAL_VETERINARY_SERVICES(Primary.MEDICAL, MedicalDetailed.VETERINARY_SERVICES),
    MEDICAL_OTHER_MEDICAL(Primary.MEDICAL, MedicalDetailed.OTHER_MEDICAL),
    PERSONAL_CARE_GYMS_AND_FITNESS_CENTERS(Primary.PERSONAL_CARE, PersonalCareDetailed.GYMS_AND_FITNESS_CENTERS),
    PERSONAL_CARE_HAIR_AND_BEAUTY(Primary.PERSONAL_CARE, PersonalCareDetailed.HAIR_AND_BEAUTY),
    PERSONAL_CARE_LAUNDRY_AND_DRY_CLEANING(Primary.PERSONAL_CARE, PersonalCareDetailed.LAUNDRY_AND_DRY_CLEANING),
    PERSONAL_CARE_OTHER_PERSONAL_CARE(Primary.PERSONAL_CARE, PersonalCareDetailed.OTHER_PERSONAL_CARE),
    GENERAL_SERVICES_ACCOUNTING_AND_FINANCIAL_PLANNING(
        Primary.GENERAL_SERVICES,
        GeneralServicesDetailed.ACCOUNTING_AND_FINANCIAL_PLANNING
    ),
    GENERAL_SERVICES_AUTOMOTIVE(Primary.GENERAL_SERVICES, GeneralServicesDetailed.AUTOMOTIVE),
    GENERAL_SERVICES_CHILDCARE(Primary.GENERAL_SERVICES, GeneralServicesDetailed.CHILDCARE),
    GENERAL_SERVICES_CONSULTING_AND_LEGAL(Primary.GENERAL_SERVICES, GeneralServicesDetailed.CONSULTING_AND_LEGAL),
    GENERAL_SERVICES_EDUCATION(Primary.GENERAL_SERVICES, GeneralServicesDetailed.EDUCATION),
    GENERAL_SERVICES_INSURANCE(Primary.GENERAL_SERVICES, GeneralServicesDetailed.INSURANCE),
    GENERAL_SERVICES_POSTAGE_AND_SHIPPING(Primary.GENERAL_SERVICES, GeneralServicesDetailed.POSTAGE_AND_SHIPPING),
    GENERAL_SERVICES_STORAGE(Primary.GENERAL_SERVICES, GeneralServicesDetailed.STORAGE),
    GENERAL_SERVICES_OTHER_GENERAL_SERVICES(Primary.GENERAL_SERVICES, GeneralServicesDetailed.OTHER_GENERAL_SERVICES),
    GOVERNMENT_AND_NON_PROFIT_DONATIONS(Primary.GOVERNMENT_AND_NON_PROFIT, GovernmentAndNonProfitDetailed.DONATIONS),
    GOVERNMENT_AND_NON_PROFIT_GOVERNMENT_DEPARTMENTS_AND_AGENCIES(
        Primary.GOVERNMENT_AND_NON_PROFIT,
        GovernmentAndNonProfitDetailed.GOVERNMENT_DEPARTMENTS_AND_AGENCIES
    ),
    GOVERNMENT_AND_NON_PROFIT_TAX_PAYMENT(
        Primary.GOVERNMENT_AND_NON_PROFIT,
        GovernmentAndNonProfitDetailed.TAX_PAYMENT
    ),
    GOVERNMENT_AND_NON_PROFIT_OTHER_GOVERNMENT_AND_NON_PROFIT(
        Primary.GOVERNMENT_AND_NON_PROFIT,
        GovernmentAndNonProfitDetailed.OTHER_GOVERNMENT_AND_NON_PROFIT
    ),
    TRANSPORTATION_BIKES_AND_SCOOTERS(Primary.TRANSPORTATION, TransportationDetailed.BIKES_AND_SCOOTERS),
    TRANSPORTATION_GAS(Primary.TRANSPORTATION, TransportationDetailed.GAS),
    TRANSPORTATION_PARKING(Primary.TRANSPORTATION, TransportationDetailed.PARKING),
    TRANSPORTATION_PUBLIC_TRANSIT(Primary.TRANSPORTATION, TransportationDetailed.PUBLIC_TRANSIT),
    TRANSPORTATION_TAXIS_AND_RIDE_SHARES(Primary.TRANSPORTATION, TransportationDetailed.TAXIS_AND_RIDE_SHARES),
    TRANSPORTATION_TOLLS(Primary.TRANSPORTATION, TransportationDetailed.TOLLS),
    TRANSPORTATION_OTHER_TRANSPORTATION(Primary.TRANSPORTATION, TransportationDetailed.OTHER_TRANSPORTATION),
    TRAVEL_FLIGHTS(Primary.TRAVEL, TravelDetailed.FLIGHTS),
    TRAVEL_LODGING(Primary.TRAVEL, TravelDetailed.LODGING),
    TRAVEL_RENTAL_CARS(Primary.TRAVEL, TravelDetailed.RENTAL_CARS),
    TRAVEL_OTHER_TRAVEL(Primary.TRAVEL, TravelDetailed.OTHER_TRAVEL),
    RENT_AND_UTILITIES_GAS_AND_ELECTRICITY(Primary.RENT_AND_UTILITIES, RentAndUtilitiesDetailed.GAS_AND_ELECTRICITY),
    RENT_AND_UTILITIES_INTERNET_AND_CABLE(Primary.RENT_AND_UTILITIES, RentAndUtilitiesDetailed.INTERNET_AND_CABLE),
    RENT_AND_UTILITIES_RENT(Primary.RENT_AND_UTILITIES, RentAndUtilitiesDetailed.RENT),
    RENT_AND_UTILITIES_SEWAGE_AND_WASTE_MANAGEMENT(
        Primary.RENT_AND_UTILITIES,
        RentAndUtilitiesDetailed.SEWAGE_AND_WASTE_MANAGEMENT
    ),
    RENT_AND_UTILITIES_TELEPHONE(Primary.RENT_AND_UTILITIES, RentAndUtilitiesDetailed.TELEPHONE),
    RENT_AND_UTILITIES_WATER(Primary.RENT_AND_UTILITIES, RentAndUtilitiesDetailed.WATER),
    RENT_AND_UTILITIES_OTHER_UTILITIES(Primary.RENT_AND_UTILITIES, RentAndUtilitiesDetailed.OTHER_UTILITIES),
    OTHER(Primary.OTHER, OtherDetailed.OTHER);

    companion object {
        fun from(categoryModel: PersonalFinanceCategory): PersonalFinanceCategoryEnum {
            // Special case to handle what I believe is a Plaid bug I saw in the wild
            if (categoryModel.primary == Primary.TRAVEL.name &&
                categoryModel.detailed == TRANSPORTATION_PUBLIC_TRANSIT.name) {
                return TRANSPORTATION_PUBLIC_TRANSIT
            }
            // Business as usual
            return values().find {
                it.primary.name == categoryModel.primary && it.name == categoryModel.detailed
            }
                ?: throw IllegalArgumentException("Failed to convert personal finance category $categoryModel to enum")
        }
    }

    fun toPersonalFinanceCategory(): PersonalFinanceCategory {
        return PersonalFinanceCategory(this.primary.name, this.name)
    }

    interface Detailed {
        val description: String
        val name: String
    }

    enum class Primary(val defaultDirection: Direction) {
        INCOME(Direction.IN),
        TRANSFER_IN(Direction.IN),
        TRANSFER_OUT(Direction.OUT),
        LOAN_PAYMENTS(Direction.OUT),
        BANK_FEES(Direction.OUT),
        ENTERTAINMENT(Direction.OUT),
        FOOD_AND_DRINK(Direction.OUT),
        GENERAL_MERCHANDISE(Direction.OUT),
        HOME_IMPROVEMENT(Direction.OUT),
        MEDICAL(Direction.OUT),
        PERSONAL_CARE(Direction.OUT),
        GENERAL_SERVICES(Direction.OUT),
        GOVERNMENT_AND_NON_PROFIT(Direction.OUT),
        TRANSPORTATION(Direction.OUT),
        TRAVEL(Direction.OUT),
        RENT_AND_UTILITIES(Direction.OUT),
        OTHER(Direction.OUT),
    }

    enum class IncomeDetailed(override val description: String) : Detailed {
        DIVIDENDS("Dividends from investment accounts"),
        INTEREST_EARNED("Income from interest on savings accounts"),
        RETIREMENT_PENSION("Income from pension payments "),
        TAX_REFUND("Income from tax refunds"),
        UNEMPLOYMENT("Income from unemployment benefits, including unemployment insurance and healthcare"),
        WAGES("Income from salaries, gig-economy work, and tips earned"),
        OTHER_INCOME("Other miscellaneous income, including alimony, social security, child support, and rental"),
    }

    enum class TransferInDetailed(override val description: String) : Detailed {
        CASH_ADVANCES_AND_LOANS("Loans and cash advances deposited into a bank account"),
        DEPOSIT("Cash, checks, and ATM deposits into a bank account"),
        INVESTMENT_AND_RETIREMENT_FUNDS("Inbound transfers to an investment or retirement account"),
        SAVINGS("Inbound transfers to a savings account"),
        ACCOUNT_TRANSFER("General inbound transfers from another account"),
        OTHER_TRANSFER_IN("Other miscellaneous inbound transactions"),
    }

    enum class TransferOutDetailed(override val description: String) : Detailed {
        INVESTMENT_AND_RETIREMENT_FUNDS("Transfers to an investment or retirement account, including investment apps such as Acorns, Betterment"),
        SAVINGS("Outbound transfers to savings accounts"),
        WITHDRAWAL("Withdrawals from a bank account"),
        ACCOUNT_TRANSFER("General outbound transfers to another account"),
        OTHER_TRANSFER_OUT("Other miscellaneous outbound transactions"),
    }

    enum class LoanPaymentsDetailed(override val description: String) : Detailed {
        CAR_PAYMENT("Car loans and leases"),
        CREDIT_CARD_PAYMENT("Payments to a credit card. These are positive amounts for credit card subtypes and negative for depository subtypes"),
        PERSONAL_LOAN_PAYMENT("Personal loans, including cash advances and buy now pay later repayments"),
        MORTGAGE_PAYMENT("Payments on mortgages"),
        STUDENT_LOAN_PAYMENT("Payments on student loans. For college tuition, refer to \"General Services - Education\""),
        OTHER_PAYMENT("Other miscellaneous debt payments"),
    }

    enum class BankFeesDetailed(override val description: String) : Detailed {
        ATM_FEES("Fees incurred for out-of-network ATMs"),
        FOREIGN_TRANSACTION_FEES("Fees incurred on non-domestic transactions"),
        INSUFFICIENT_FUNDS("Fees relating to insufficient funds"),
        INTEREST_CHARGE("Fees incurred for interest on purchases, including not-paid-in-full or interest on cash advances"),
        OVERDRAFT_FEES("Fees incurred when an account is in overdraft"),
        OTHER_BANK_FEES("Other miscellaneous bank fees"),
    }

    enum class EntertainmentDetailed(override val description: String) : Detailed {
        CASINOS_AND_GAMBLING("Gambling, casinos, and sports betting"),
        MUSIC_AND_AUDIO("Digital and in-person music purchases, including music streaming services"),
        SPORTING_EVENTS_AMUSEMENT_PARKS_AND_MUSEUMS("Purchases made at sporting events, music venues, concerts, museums, and amusement parks"),
        TV_AND_MOVIES("In home movie streaming services and movie theaters"),
        VIDEO_GAMES("Digital and in-person video game purchases"),
        OTHER_ENTERTAINMENT("Other miscellaneous entertainment purchases, including night life and adult entertainment"),
    }

    enum class FoodAndDrinkDetailed(override val description: String) : Detailed {
        BEER_WINE_AND_LIQUOR("Beer, Wine & Liquor Stores"),
        COFFEE("Purchases at coffee shops or cafes"),
        FAST_FOOD("Dining expenses for fast food chains"),
        GROCERIES("Purchases for fresh produce and groceries, including farmers' markets"),
        RESTAURANT("Dining expenses for restaurants, bars, gastropubs, and diners"),
        VENDING_MACHINES("Purchases made at vending machine operators"),
        OTHER_FOOD_AND_DRINK("Other miscellaneous food and drink, including desserts, juice bars, and delis"),
    }

    enum class GeneralMerchandiseDetailed(override val description: String) : Detailed {
        BOOKSTORES_AND_NEWSSTANDS("Books, magazines, and news "),
        CLOTHING_AND_ACCESSORIES("Apparel, shoes, and jewelry"),
        CONVENIENCE_STORES("Purchases at convenience stores"),
        DEPARTMENT_STORES("Retail stores with wide ranges of consumer goods, typically specializing in clothing and home goods"),
        DISCOUNT_STORES("Stores selling goods at a discounted price"),
        ELECTRONICS("Electronics stores and websites"),
        GIFTS_AND_NOVELTIES("Photo, gifts, cards, and floral stores"),
        OFFICE_SUPPLIES("Stores that specialize in office goods"),
        ONLINE_MARKETPLACES("Multi-purpose e-commerce platforms such as Etsy, Ebay and Amazon"),
        PET_SUPPLIES("Pet supplies and pet food"),
        SPORTING_GOODS("Sporting goods, camping gear, and outdoor equipment"),
        SUPERSTORES("Superstores such as Target and Walmart, selling both groceries and general merchandise"),
        TOBACCO_AND_VAPE("Purchases for tobacco and vaping products"),
        OTHER_GENERAL_MERCHANDISE("Other miscellaneous merchandise, including toys, hobbies, and arts and crafts"),
    }

    enum class HomeImprovementDetailed(override val description: String) : Detailed {
        FURNITURE("Furniture, bedding, and home accessories"),
        HARDWARE("Building materials, hardware stores, paint, and wallpaper"),
        REPAIR_AND_MAINTENANCE("Plumbing, lighting, gardening, and roofing"),
        SECURITY("Home security system purchases"),
        OTHER_HOME_IMPROVEMENT("Other miscellaneous home purchases, including pool installation and pest control"),
    }

    enum class MedicalDetailed(override val description: String) : Detailed {
        DENTAL_CARE("Dentists and general dental care"),
        EYE_CARE("Optometrists, contacts, and glasses stores"),
        NURSING_CARE("Nursing care and facilities"),
        PHARMACIES_AND_SUPPLEMENTS("Pharmacies and nutrition shops"),
        PRIMARY_CARE("Doctors and physicians"),
        VETERINARY_SERVICES("Prevention and care procedures for animals"),
        OTHER_MEDICAL("Other miscellaneous medical, including blood work, hospitals, and ambulances"),
    }

    enum class PersonalCareDetailed(override val description: String) : Detailed {
        GYMS_AND_FITNESS_CENTERS("Gyms, fitness centers, and workout classes"),
        HAIR_AND_BEAUTY("Manicures, haircuts, waxing, spa/massages, and bath and beauty products "),
        LAUNDRY_AND_DRY_CLEANING("Wash and fold, and dry cleaning expenses"),
        OTHER_PERSONAL_CARE("Other miscellaneous personal care, including mental health apps and services"),
    }

    enum class GeneralServicesDetailed(override val description: String) : Detailed {
        ACCOUNTING_AND_FINANCIAL_PLANNING("Financial planning, and tax and accounting services"),
        AUTOMOTIVE("Oil changes, car washes, repairs, and towing"),
        CHILDCARE("Babysitters and daycare"),
        CONSULTING_AND_LEGAL("Consulting and legal services"),
        EDUCATION("Elementary, high school, professional schools, and college tuition"),
        INSURANCE("Insurance for auto, home, and healthcare"),
        POSTAGE_AND_SHIPPING("Mail, packaging, and shipping services"),
        STORAGE("Storage services and facilities"),
        OTHER_GENERAL_SERVICES("Other miscellaneous services, including advertising and cloud storage "),
    }

    enum class GovernmentAndNonProfitDetailed(override val description: String) : Detailed {
        DONATIONS("Charitable, political, and religious donations"),
        GOVERNMENT_DEPARTMENTS_AND_AGENCIES("Government departments and agencies, such as driving licences, and passport renewal"),
        TAX_PAYMENT("Tax payments, including income and property taxes"),
        OTHER_GOVERNMENT_AND_NON_PROFIT("Other miscellaneous government and non-profit agencies"),
    }

    enum class TransportationDetailed(override val description: String) : Detailed {
        BIKES_AND_SCOOTERS("Bike and scooter rentals"),
        GAS("Purchases at a gas station"),
        PARKING("Parking fees and expenses"),
        PUBLIC_TRANSIT("Public transportation, including rail and train, buses, and metro"),
        TAXIS_AND_RIDE_SHARES("Taxi and ride share services"),
        TOLLS("Toll expenses"),
        OTHER_TRANSPORTATION("Other miscellaneous transportation expenses"),
    }

    enum class TravelDetailed(override val description: String) : Detailed {
        FLIGHTS("Airline expenses"),
        LODGING("Hotels, motels, and hosted accommodation such as Airbnb"),
        RENTAL_CARS("Rental cars, charter buses, and trucks"),
        OTHER_TRAVEL("Other miscellaneous travel expenses"),
    }

    enum class RentAndUtilitiesDetailed(override val description: String) : Detailed {
        GAS_AND_ELECTRICITY("Gas and electricity bills"),
        INTERNET_AND_CABLE("Internet and cable bills"),
        RENT("Rent payment"),
        SEWAGE_AND_WASTE_MANAGEMENT("Sewage and garbage disposal bills"),
        TELEPHONE("Cell phone bills"),
        WATER("Water bills"),
        OTHER_UTILITIES("Other miscellaneous utility bills"),
    }

    enum class OtherDetailed(override val description: String) : Detailed {
        OTHER("Other"),
    }
}

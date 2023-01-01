# Firefly Plaid Connector 2
Connector to pull Plaid financial data into the Firefly finance tool.

Inspired by [firefly-plaid-connector](https://gitlab.com/GeorgeHahn/firefly-plaid-connector/).

![Alt text](doc/images/thing.jpg "Title")

# Running
These are basic instructions for installing and running the connector. See further topics below for more details.

## Running the JAR Directly 
This method is recommended due to it being simpler than Docker.

1. Ensure you have a JRE or JDK for at least Java 17.
2. Download the latest JAR from the [releases page](https://github.com/dvankley/firefly-plaid-connector-2/releases).
2. Move the JAR to your desired working directory. 
3. Make a `persistence/` subdirectory in your working directory for the connector to persist data to that's writeable 
by the user running the connector.
4. Set up a configuration file. I suggest copying the existing `application.yml` and modifying as needed.
See [below](#config) for details on configuration.
5. Run the connector, for instance with `java -jar connector.jar --spring.config.location=application.yml`

## Running via Docker
New versions of the Docker image are pushed to GHCR with each release.
The latest version is available at `ghcr.io/dvankley/firefly-plaid-connector-2:latest`.

### Requirements
The application container requires the following:
1. An application configuration file to read from.
   2. See [below](#config) for details on configuration.
   2. The `SPRING_CONFIG_LOCATION` environment variable can be 
   used to set the location (in the container) of the application configuration file.
2. A directory to write the sync cursor file to (if run in polled mode).
   3. The `FIREFLYPLAIDCONNECTOR2_POLLED_CURSORFILEDIRECTORYPATH` environment variable can be
      used to set the location (in the container) of the directory that will contain the sync cursor file.

The example below uses bind mounts for these purposes for
simplicity, but you can also use volumes if you want.
If using volumes you will need to use an intermediate container
to write the application configuration file to a volume, as well as setting permissions to allow the
application user `cnb` to write to the cursor file directory's volume.

### Example Docker Run Command
```shell
docker run \
--mount type=bind,source=/host/machine/application/config/file/directory,destination=/opt/fpc-config,readonly \
--mount type=bind,source=/host/machine/writeable/directory,destination=/opt/fpc-cursors \
-e SPRING_CONFIG_LOCATION=/opt/fpc-config/application.yml \
-e FIREFLYPLAIDCONNECTOR2_POLLED_CURSORFILEDIRECTORYPATH=/opt/fpc-cursors \
-t firefly-plaid-connector-2
```

# Concepts
## Mode
The connector can be run in either `batch` or `polled` mode.
### Batch
Uses the [Plaid transactions get](https://plaid.com/docs/api/products/transactions/#transactionsget) endpoint to
pull historical Plaid transaction data, convert it to Firefly transactions, and write them to Firefly.

This is typically used when you're first setting up your Firefly instance and you want to backfill existing transactions
before running indefinitely in `polled` mode.

`Batch` mode can be memory intensive if you're pulling a large volume of transactions, so if your Firefly server is
running on a low-spec VPS (like mine), then I recommend running your large `batch` mode pull on a higher spec machine
(like your home computer or whatever). It can still be pointed at your existing Firefly server with no problem.

### Polled
Uses the [Plaid sync](https://plaid.com/blog/transactions-sync/) endpoint to periodically pull down transaction data,
convert it to Firefly transactions, and write them to Firefly.

When started in this mode, the connector will not pull any past transactions, but will check for new transactions every 
`fireflyPlaidConnector2.polled.syncFrequencyMinutes`.

## Firefly Transfers
The most complex part of the connector by far is the transfer matching logic.
Firefly has a notion of [transfers](https://docs.firefly-iii.org/firefly-iii/concepts/transactions/#transfers),
which are special transactions that instead of the usual flow of money from an asset account into an expense account
or from a revenue account into an asset account, represent the flow of money from one asset account into another.

For me this typically happens when I'm making payments on credit cards or the like (because I have credit cards set
up as asset accounts instead of liability accounts because that's how Firefly works).

Plaid has no notion of inter-account transactions; transaction data only references the account that it's on.

We try to bridge these by matching two Plaid transactions on different asset accounts and converting them into a single
Firefly transfer. You can see the full logic in `TransactionConverter.sortByPairs`, but the summary is that the
connector searches for pairs of Plaid transfer-type transactions with inverse amounts (i.e. $100 and -$100)
that are within `fireflyPlaidConnector2.transferMatchWindowDays` of each other. This isn't flawless, but
is sufficient in most cases.

Note that if the connector attempts to convert an existing non-transfer Firefly transaction and an incoming Plaid
transaction to a Firefly transfer, the existing Firefly transaction will be deleted and the new transfer
transaction will be created because the Firefly API does not support converting existing transaction types.

## Initial Balances
If `fireflyPlaidConnector2.batch.setInitialBalance` is set to `true`, the connector will try to create "initial balance"
transactions for each Firefly account that result in the current Firefly balance for each account equalling the
current balance that [Plaid reports](https://plaid.com/docs/api/products/balance/#accountsbalanceget) for that account.
This is determined by summing up all transactions pulled during
this run for that account, then subtracting that from the Plaid-reported current balance to get the amount for the 
"initial balance" transaction.

For Firefly asset accounts that are of the "credit card" type, the Plaid balance is interpreted as a negative rather
than a positive value. This is because Plaid always reports balances as positive (as far as I can tell), and it's
assumed that a Firefly account set as a credit card is linked to a Plaid account that's also a credit card.

Ensure that the current balance in the target Firefly accounts are 0 before using this feature.

Because the initial balance transaction amount is determined from the transactions pulled in this batch, I do not
recommend enabling this feature unless you're pulling all of the transactions you intend to backfill for a given
account (as opposed to just filling in some gaps).

Note that the [Plaid balance endpoint](https://plaid.com/docs/api/products/balance/#accountsbalanceget)
(or the underlying institution data source) is kind of crappy. To wit:
* There is a `lastUpdatedDatetime` field in the response schema, but according to the Plaid documentation it's only
populated for Capital One for whatever reason,
and due to that it's impossible to tell exactly what point in time the Plaid balance represents.
  * The upshot of this is that if the Plaid balance is out of date by a transaction or two,
  the "initial balance" transaction amount will also be a bit off. Plan accordingly.
* According to the docs, the Plaid balance endpoint forces a synchronous update from the underlying institution data
source, which can cause this part of the connector's run to take a while. The Plaid API timeout has been
[adjusted](https://github.com/dvankley/firefly-plaid-connector-2/blob/a73fb692937984b64af34a8047c05fcef03cc088/src/main/kotlin/net/djvk/fireflyPlaidConnector2/api/ApiConfiguration.kt#L29)
to account for this, but it still fails with surprising regularity.

# Configuration
## Plaid
As you have probably guessed from the name, Plaid is an important part of using this connector.
To use Plaid, you will need an account. As of this writing, you can sign up for a developer account for free, which
should be sufficient for the purposes of this connector.

### Basic Credentials
Once you've signed up for Plaid, you should be provided a client id and secret, which go in the application config
file where you'd expect (`fireflyPlaidConnector2.plaid.clientId` and `fireflyPlaidConnector2.plaid.secret`).

### Connecting Accounts
Next up, you need to connect Plaid to your various financial institutions. The easiest way to do this is to run
[Plaid Quickstart](https://plaid.com/docs/quickstart/) locally. I found the Docker experience to be fairly painless.
I recommend copying the `.env.example` file to a new `.env` file, filling in your credentials, setting `PLAID_ENV` to
`development` (unless you're using `production` for some reason), and setting `PLAID_PRODUCTS` to `transactions`. Note that
if you leave `PLAID_PRODUCTS` set to the default `auth,transactions`, you won't be able to connect to some of the
financial institutions you might expect because they don't support the `auth` product.

Once you have Quickstart running, just follow the UI prompts to connect to your financial institutions.
For each institution you connect to, Plaid should give you an `item_id` and an `access_token`. Make a note of both.
Each institution can contain multiple accounts (i.e. your bank has both your savings and checking account), so you
will need to make an additional call to the Plaid API to get individual account ids. Example callout using `httpie`:
```
http POST https://development.plaid.com/accounts/get \
    client_id=yourclientid \
    secret=yoursecret \
    access_token=access-development-your-items-access-token
```
This will give you a list of account ids that belong to that item/financial institution. Make a note of these, as
you will need to enter them into the connector's configuration file.

## Configuration File
The configuration file is unsurprisingly where most of the configuration of the connector takes place.
I recommend copying the [`application.yml` file](https://github.com/dvankley/firefly-plaid-connector-2/blob/main/src/main/resources/application.yml)
and customizing it to your preferences. Each property should be well documented in the comments of that file.

# Reference Workflow
This is my workflow for using the connector with Firefly.
Of course, you don't have to use it exactly like this, but you may find this useful for reference in thinking through your own workflow.

## 

# Troubleshooting
## Logs
The `logging` key in the application configuration file controls the logging level of various packages and classes in
the application. Setting `logging.level.net.djvk` to `DEBUG` or `TRACE` is recommended if you're having problems,
as it will log additional info that should help diagnose the issue.

## Reporting Issues
If you have an issue, feel free to report it via the Github issue tracker. I am actively maintaining this project
but my available time is finite, so the odds of your issue being addressed will be increased if you include relevant
logs at the `TRACE` level with your issue report.
Writing a test case (i.e. in `net.djvk.fireflyPlaidConnector2.transactions.TransactionConverterTest`) to demonstrate
your issue greatly increases the chances of me fixing it quickly. If test infrastructure is missing to test the
element of the code you see an issue with, let me know and I can work on improving that.

# Development
## Setup
Setup should be identical to any other Spring Boot/Gradle application.
I recommend adding an additional configuration file (i.e. `application-dev.yml`) and enabling the corresponding Spring
profile (i.e. `dev`) to allow you to persist and iterate on your local configuration.

Connecting to the Plaid `development` environment as usual should be fine for development.
I recommend setting up a local copy of Firefly for development purposes, especially one that you can easily backup
and restore the database for to minimize your feedback loop on testing things.

## Guidelines
I don't currently have firm guidelines yet, but I will adopt some if I ever get contributions. For now the main guideline
is to have a test covering the changes you make.

# FAQ
* Why did you make a new program rather than contributing to [firefly-plaid-connector](https://gitlab.com/GeorgeHahn/firefly-plaid-connector/)?
  * I initially tried firefly-plaid-connector, but I had a few issues with it, and it didn't fully support Plaid categories.
I tried to set it up for development locally, but after about an hour trying to get the right version of the .NET SDK to work,
I decided I was better off making my own gravy. So here we are.



Topics to ensure are covered:
- Set PLAID_PRODUCTS=auth,transactions to just transactions
- Run bulk mode from your local machine
- Why we do delete/create rather than update (because Firefly does not support updating transaction types i.e. from deposit to transfer)
- When setting initial balances, make sure that the balance in the account before bulk importing is 0
- When setting initial balances, the Plaid balance endpoint doesn't return `lastUpdatedDatetime` for some reason, so if
the balance isn't completely up to date in Plaid, your adjusted balance may be off by the last transaction (or few).
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
financial institutions you might expect to be able to because they don't support the `auth` product.

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
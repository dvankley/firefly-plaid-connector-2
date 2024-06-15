# Firefly Plaid Connector 2

This open-source application helps you to pull data from your bank into Firefly III by using Plaid to obtain transactions from your bank and convert them into a firefly entry. You will no longer need to export CSV or Quickbook files or manually type in transaction data to Firefly.

[Firefly III](https://firefly-iii.org/) is a (self-hosted) manager for your personal finances.

[Plaid](https://plaid.com/) is a financial technology (fintech) company that allows users to securely connect their bank accounts and credit card providers to financial services apps. While this connector application is free to use, Plaid is NOT. Expect to pay for Plaid services.

Firefly Plaid Connector 2 is inspired by [firefly-plaid-connector](https://gitlab.com/GeorgeHahn/firefly-plaid-connector/).

# How it Works
This application makes it easy to load transactions from your bank into Firefly III

### Connect Your Account

You will configure the connector to sync your Firefly III and Plaid account. See the [Get Started](#getting-started) section below for more information

### Get Bank Data Using Plaid

The connector uses Plaid to obtain transaction data from your banks and credit cards. The connector can be run in either `batch` or `polled` mode.

#### Batch Mode
This is typically used when you're first setting up your Firefly instance and you want to backfill existing transactions before running indefinitely in `polled` mode.You could in theory use this mode if you wanted to only pull in new transactions when you want to.

Batch mode uses the Plaid [transactions GET endpoint](https://plaid.com/docs/api/products/transactions/#transactionsget) to pull transaction data. 

`Batch` mode can be memory intensive. If you're pulling a large volume of transactions, you plan to run the connector on the same computer as Firefly, and that computer is low-spec please consider running the connector on a different computer (like your personal computer) for the large `batch` mode pull first.

#### Polling Mode
Polling mode uses the Plaid [transactions sync endpoint](https://plaid.com/docs/api/processor-partners/#processortransactionssync) to periodically pull down transaction data. More information can be found [here](https://plaid.com/blog/transactions-sync/).

This mode will not pull any past transactions, but will check for new transactions every based on the time you configure in your `application.yml` file. See [configuration](#configuration) for more details.

_The last sync position of each account will be stored in `persistence/plaid_sync_cursors.txt`._

### Convert Transactions

The connector will convert the Plaid transactions into Firefly transactions automatically. You can still take advantage of [Firefly Rules](https://docs.firefly-iii.org/how-to/firefly-iii/features/rules/) to automate your tracking preferences.

#### Deposits and Withdrawals
The connector will create transactions in your Firefly instance for each Plaid deposit and withdrawal using the transactions API.

For Firefly asset accounts that are of the "credit card" type, the incoming Plaid balance is interpreted as a negative value. Plaid always reports balances as positive a positive value, so the connector inverts the value to track purchases properly.

#### Transfers
Typical transactions are usually either a withdrawal or a deposit. Firefly has a concept of [transfers](https://docs.firefly-iii.org/firefly-iii/concepts/transactions/#transfers), which represent a withdrawal from one account and a matching deposit into another account. Common cases of this are paying off credit cards or moving money from a checking account into a savings account.

The connector is capable of automatically creating transfers between accounts. However **it is not always accurate**. The connector tries to match two Plaid transactions on different asset accounts and convert them into a single Firefly transfer. Plaid has no understanding of inter-account transactions; There is a withdrawal on one account and a deposit on another account - often on different days. Sometimes the connector fails to convert these transactions, but is sufficient in most cases. 

If the connector fails to create a transfer there will be a withdrawal from one asset and a deposit on another - no data is lost. You will need to manually fix these records if you want a transfer transaction.

To get technical, the connector searches for pairs of Plaid transfer-type transactions with inverse amounts (i.e. $100 and -$100) that are within `fireflyPlaidConnector2 transferMatchWindowDays` of each other. This logic is defined in [sortByPairs()](https://github.com/dvankley/firefly-plaid-connector-2/blob/main/src/main/kotlin/net/djvk/fireflyPlaidConnector2/transactions/TransactionConverter.kt). The connector cannot convert withdrawals and deposits into a transfer on Firefly so it will create a new transfer transaction and delete the appropriate withdrawal and deposit.


# Pricing (Plaid)
While the Firefly III & Firefly Plaid Connector are free to use - Plaid is a paid service. Your Plaid account will require production environment access. Running this connector application using production APIs will you cost money. At the time of writing this Plaid will bill you:
 - $0.30/institution/month for the `transactions` API (required by connector)
 - $0.10 per call, for the `balance` API (not required, very costly)

If you're an existing user of the development environment, you _may_ be able to get away with "free limited production access" per [this comment](https://github.com/dvankley/firefly-plaid-connector-2/issues/67#issuecomment-2077358653), but don't plan on it.

If you're an existing development environment user and wish to migrate there are instructions in the [migrate to the plaid production environment](#migrating-plaid-from-development-to-production) section.

### Transactions

The [transactions API](https://plaid.com/docs/api/products/transactions/) contains all the information needed to create a transaction in Firefly representing your bank accounts activity. 

The connector has the ability to run in two modes: Polling and Batching. In both cases it will use the transactions API.

### Initial Balances (Balance API)

**WARNING: This feature is can be very expensive to use. It is not required by the connector, only enable this feature if you know what you are doing.**

The Initial Balance feature creates "initial balance" transactions for each Firefly account. The connector will sum up all transactions from the API call for that account, then subtract that from the Plaid-reported current balance to get the amount for the "initial balance" transaction. This will result in the current Firefly balance matching the current balance reported by [Plaid reports](https://plaid.com/docs/api/products/balance/#accountsbalanceget) for that account.

The transaction amount is determined from the all transactions pulled in this batch. Therefore this feature should only be enabled if you're pulling all of the transactions you intend to backfill for a given account (as opposed to just filling in some gaps).

**WARNING: The current balance in the target Firefly assets must be ZERO before using this feature.**

There are some known issues with the [Plaid balance endpoint](https://plaid.com/docs/api/products/balance/#accountsbalanceget):
 - There is a `lastUpdatedDatetime` field in the response schema, but according to the Plaid documentation it's only populated for Capital One. This makes it impossible to tell what point in time the Plaid balance represents. If the Plaid balance is out of date by a transaction or two, the "initial balance" transaction amount will also be inaccurate. Plan accordingly.
 - According to the docs, the Plaid balance endpoint forces a synchronous update from the underlying institution data source, which can cause this part of the connector's run to take a while. The Plaid API timeout has been [adjusted](https://github.com/dvankley/firefly-plaid-connector-2/blob/a73fb692937984b64af34a8047c05fcef03cc088/src/main/kotlin/net/djvk/fireflyPlaidConnector2/api/ApiConfiguration.kt#L29) to account for this, but it still fails with surprising regularity.

To enable this feature see the [configuration](#configuration) section


# Getting Started

### Set up Plaid

Setting up plaid is separate from running the connector. We need a Plaid production account in good standing to obtain an API Key for our application

#### Plaid Oauth Registration
If you want access to US Oauth institutions, you need to jump through [some additional hoops](https://dashboard.plaid.com/settings/compliance/us-oauth-institutions). Based on my brief conversation with Plaid support and my personal experience, you can skim through the company info and security questionnaire pretty quickly, using dummy names, logos, and answering "I'm a hobbyist using this for personal use" for all the security questions.

Once you've completed all the requirements for Oauth access, the applicable institutions should work.

#### Plaid API Credentials
Once you've signed up for Plaid and enabled production access you should be provided a client id and secret, which go in the [application config file](#configuration).

#### Connecting Bank Accounts to Plaid
Next up, you need to connect Plaid to your various financial institutions. The easiest way to do this is to run [Plaid Quickstart](https://plaid.com/docs/quickstart/). 

##### Set up the Plaid Quickstart
We will use the Docker Setup in this example

You should:
 - Copy the `.env.example` file to a new `.env` file
 - Fill in your credentials
 - Set `PLAID_ENV` to `development` or `production`
 - Set `PLAID_PRODUCTS` to `transactions`. If you leave `PLAID_PRODUCTS` set to the default `auth,transactions`, you won't be able to connect to some of the financial institutions you might expect because they don't support the `auth` product. 
 - If you wish to use the [initial balances](#initial-balances) feature you will need to add `balances` to the `PLAID_PRODUCTS` field

##### Connect Your Banks

Once you have Quickstart running, follow the UI prompts to connect to your financial institutions. For each institution you connect to, Plaid should give you an `item_id` and an `access_token`. Make a note of both. 

Each institution can contain multiple accounts (i.e. your bank has both your savings and checking account), so you will need to make an additional call to the Plaid API to get individual account ids. 

Example using `httpie`:
```
http POST https://production.plaid.com/accounts/get \
    client_id=yourclientid \
    secret=yoursecret \
    access_token=access-production-your-items-access-token
```

This will give you a list of account ids that belong to that item/financial institution. Make a note of these, as you will need to enter them into the connector's configuration file.

### Dependencies

This README assumes you already have
1. A Plaid account
2. A running Firefly instance & account

To run this app you will need

| Name       | Minimum Version |
|------------|-----------------|
|Firefly     |v6.1.2           |
|Java        |Java 17          |


### Deployment

This is the technically steps to deploying the connector. There is also an [applied example](#applied-setup-example) below that explains the process in more detail.

#### Running as a JAR Executable

1. Download the latest JAR from the [releases page](https://github.com/dvankley/firefly-plaid-connector-2/releases).
2. Move the JAR to your desired working directory. 
3. Make a `persistence/` subdirectory in your working directory for the connector to persist data to that's writeable by the user running the connector.
4. Copy the [application.yml](https://github.com/dvankley/firefly-plaid-connector-2/blob/main/src/main/resources/application.yml) and modify as needed. See [below](#configuration) for details on configuration.
5. Run the connector via terminal. Example: `java -jar connector.jar --spring.config.location=application.yml`

#### Running via Docker CLI
New versions of the Docker image are pushed to GHCR with each release. The latest version is available at `ghcr.io/dvankley/firefly-plaid-connector-2:latest`.

##### Requirements
The application container requires the following:
1. An application configuration file. See the [configuration section](#configuration) for details. You will set the `SPRING_CONFIG_LOCATION` environment variable in the run command so the image knows where the configuration is at run time.
2. A location for the sync cursor file (this tracks information for [polling mode](#polling-mode)). You will set the `FIREFLYPLAIDCONNECTOR2_POLLED_CURSORFILEDIRECTORYPATH` environment variable in the run command.

For simplicity we will make use of bind mounts. Here is an example of how to run the connector:

##### Example Docker Run Command (bind mounts)
```shell
docker run \
--mount type=bind,source=/host/machine/application/config/file/directory,destination=/opt/fpc-config,readonly \
--mount type=bind,source=/host/machine/writeable/directory,destination=/opt/fpc-cursors \
-e SPRING_CONFIG_LOCATION=/opt/fpc-config/application.yml \
-e FIREFLYPLAIDCONNECTOR2_POLLED_CURSORFILEDIRECTORYPATH=/opt/fpc-cursors \
-t firefly-plaid-connector-2
```

##### Example Docker Run Command (volumes)

You may opt to use volumes instead, however, you will need to use an intermediate container to write the application configuration file to a volume, as well as setting permissions to allow the application user `cnb` to write to the cursor file directory's volume.

#### Deploy with Docker Compose
 1. Pull down the [docker-compose-polled.yml](https://raw.githubusercontent.com/dvankley/firefly-plaid-connector-2/main/docker-compose-polled.yml) or [docker-compose-batch.yml](https://raw.githubusercontent.com/dvankley/firefly-plaid-connector-2/main/docker-compose-batch.yml) files. Pick the example based on the mode you wish to run in. See [modes](#get-bank-data-using-plaid) for more information. Copy the raw file with the exact same whitespace, otherwise you'll have issues. YAML is very picky about whitespace.
 2. Set the `HOST_APPLICATION_CONFIG_FILE_LOCATION` environment variable to point to your [config file](#configuration) (or just manually insert your path in the compose file).
 3. If running in polled mode (and thus using `docker-compose-polled.yml`), you will need to location to store the polled mode cursor file which tracks the last sync state. Create a directory on your host machine that is writable by anyone, then pass its location in as `HOST_PERSISTENCE_DIRECTORY_LOCATION`. _(Using a bind mount for this is kind of crappy, but I couldn't find a good way to make the Spring Boot Gradle bootBuildImage plugin set perms on a named volume cleanly. Suggestions welcome)_
4. Within the directory of your docker compose file run `docker-compose up`.

#### Building Your Own Docker Image

You may opt to build your own docker image from source. Once built, you may follow the same steps to deploy using docker as stated above.

To build your own image run the command:

`./gradlew bootBuildImage --imageName=your-docker-registry/firefly-plaid-connector-2`

### Configuration

Copy the [`application.yml` file](https://github.com/dvankley/firefly-plaid-connector-2/blob/main/src/main/resources/application.yml)
and customizing it to your preferences. Each property is documented in the comments of that file.

Set the Plaid API Key information `fireflyPlaidConnector2.plaid.clientId` & `fireflyPlaidConnector2.plaid.secret`

Set the frequency of polling for new data (Polling mode only)
`fireflyPlaidConnector2.polled.syncFrequencyMinutes`.

To enable the initial balance feature `fireflyPlaidConnector2.batch.setInitialBalance` to `true`.

The `logging` key in the application configuration file controls the logging level of various packages and classes in the application. Setting `logging.level.net.djvk` to `DEBUG` or `TRACE` is recommended if you're having problems, as it will log additional info that should help diagnose the issue.


# Applied Setup Example
This is my workflow for using the connector with Firefly. Of course, you don't have to use it exactly like this, but you may find this useful for reference in thinking through your own workflow.

1. Set up your Plaid developer account, connect to your various financial institutions, and set up the connector
application configuration file as described in [Configuration](#configuration).
2. Plan your category mapping
   1. Determine what Firefly [budgets](https://docs.firefly-iii.org/firefly-iii/concepts/budgets/)
   and [categories](https://docs.firefly-iii.org/firefly-iii/concepts/categories/) you want to use.
      1. Note the [functional difference](https://docs.firefly-iii.org/firefly-iii/concepts/budgets/#the-difference-between-budgets-and-categories) between the two.
      2. In my case, as recommended by the Firefly documentation, we used budgets for expenses that are optional and
      should have a limit (or target) amount each month. We used categories for expenses that are not optional or
      should not have a limited amount each month.
      3. You may want to just put everything in budgets (and not set amounts for categories that don't need them)
      as it makes the charts and reports a bit easier to read.
   2. Determine the mapping between your Firefly budgets/categories and Plaid categories.
      1. The Plaid category taxonomy includes "primary" categories and "detailed" subcategories.
         1. See the [official reference CSV](https://plaid.com/documents/transactions-personal-finance-category-taxonomy.csv)
         2. Or if you prefer, see the connector's [parsed category list](https://github.com/dvankley/firefly-plaid-connector-2/blob/a31977ff28261593966df66e0e5ba6da07db9746/src/main/kotlin/net/djvk/fireflyPlaidConnector2/api/plaid/models/PersonalFinanceCategoryEnum.kt#L159).
      2. Assign Plaid primary or detailed categories to your Firefly budgets and categories.
         1. Each Plaid primary or detailed category may only be assigned to one Firefly budget or category.
         2. Multiple different Plaid primary or detailed categories may be assigned to a single Firefly budget or category.
            1. For example, you might assign both `FOOD_AND_DRINK.BEER_WINE_AND_LIQUOR` and `ENTERTAINMENT` to a 
            "Spending" budget.
         3. You can assign both a Plaid primary category and its detailed subcategories to different Firefly budgets
         or categories; just make a careful note in these cases as you have to be careful when building the corresponding
         Firefly rules later.
3. Implement your Firefly budgets/categories and corresponding rules
   1. Creating budgets and categories is straightforward. You do not need amounts for budgets at this time.
   2. Rules
      1. I created a rule group for Plaid category processing, then another rule group below it (and thus overriding it)
      to handle case-by-case overrides of specific transactions that Plaid didn't categorize the way I wanted.
      2. For Plaid category processing, my basic rule template is:
         1. Trigger: when a transaction is created
         2. Stop processing: `true`
         3. Strict mode: `false`
         4. Rule Triggers: "Any tag is...": `plaid-detailed-cat-coffee` etc.
            1. Add additional tag triggers if multiple Plaid categories are assigned to a single Firefly budget or category
         5. Action: "Set Budget (or Category) to...": corresponding Firefly budget or category name
      3. Consider using [@514amir's] [automated rule setup script](https://github.com/dvankley/firefly-plaid-connector-2/blob/main/plaidfireflyrulecreator.sh)
      to simplify this step.
4. Save a snapshot of your Firefly database state in case the next steps don't do what you want
   1. This step is optional but recommended.
   2. If Firefly's using a Sqlite database, all you have to do is copy the database file. If using Mysql or Postgres,
   you will need to use the appropriate backup and restore tooling.
5. Run the connector in `batch` mode
   1. I recommend doing this connector run on a higher-spec machine (i.e. your laptop or whatever) to give it all the
   memory it needs. It doesn't matter where you run it as long as the connector can make a network connection to your
   Firefly server.
   2. The `fireflyPlaidConnector2.batch.maxSyncDays` property is up to you. I used 2 years for my initial backfill,
   but your value will depend on your needs and patience for the process.
      1. For what it's worth, the performance bottleneck is Firefly handling transaction inserts. This isn't a system
      resource thing either, as it took about as long on my M1 Max as it did on a $5 VPS.
   3. Keep an eye on the connector's logs to ensure nothing's gone wrong.
6. Go through transaction reports and add additional rules for any categorization gaps
   1. I went through transactions without budgets for each month and added new override rules as needed.
      1. Transactions without budgets in Firefly can be viewed by navigating to Budgets in the sidebar, selecting
      the desired month in the Period Navigator at the top, then clicking the "Expenses without budget" link in the
      very bottom left of the window.
   2. The "Apply rule X to a selection of your transactions" feature in the Firefly Automations UI is very useful
   for filling in budget/category gaps after you've already run a transaction import.
7. If anything's gone wrong, restore your database snapshot from before and try again.
8. Set up the connector to run in `polled` mode.
   1. I created a `systemd` unit to run the connector on my Debian VPS, your mileage may vary.
      1. You will of course need to set up users, permissions, directories, etc. to support this.

```systemd
[Unit]
Description=Firefly Plaid Connector 2

[Service]
WorkingDirectory=/opt/firefly-plaid-connector
ExecStart=/bin/java -Xms128m -Xmx1024m -jar firefly-plaid-connector.jar --spring.profiles.active=prod
User=firefly-plaid-connector
Type=simple
Restart=on-failure
RestartSec=10
Environment=“SPRING_CONFIG_LOCATION=/opt/firefly-plaid-connector/application-prod.yml”

[Install]
WantedBy=multi-user.target

```


# Troubleshooting

### Known Issues

#### When setting up access to a provider from the Plaid Quick Start I'm getting a message "Something went wrong".
Several institutions are restricting access to development access accounts. An approved paid production account will need to be setup with Plaid to gain access to these accounts. This can also surface as `INSTITUTION_NO_LONGER_SUPPORTED` or `UNAUTHORIZED_INSTITUTION` errors.

#### I'm getting an `ITEM_LOGIN_REQUIRED` error when running the connector.
Your session between plaid and your bank likely expired or your bank credentials changed. There are two methods for resolving the error, described below. You can find the access token for the account in question on the log line above the exception log. The access token will indicate which account is effected.

**Update The Credentials (RECOMMENDED)**

This is the recommended method of resolving this issue, although it's a bit more complex than create mode.
1. Check out https://github.com/dvankley/quickstart
2. Start the frontend and the java backend per the instructions in the README and verify that the frontend loads correctly at `https://localhost:3000`.
3. Find the access token value for the account with the `ITEM_LOGIN_REQUIRED` error (it should be in the connector logs just above the exception).
4. Navigate to `https://localhost:3000?input_access_token=$yourAccessTokenHere` in your browser.
5. Complete the Link flow in the UI.

After completing these steps, your account credentials should be fixed and the connector should resolve itself on its next run (assuming you're running in polled mode).

If you're getting this error frequently, check if you have MFA enabled on your account with the corresponding financial institution. MFA can cause high frequency invalidation of Plaid account credentials, so consider disabling it. Obviously compromising your security posture to use this connector isn't great, so hopefully your institution provides limited permission accounts for service access.
   
Also note that CIBC currently has [this issue](https://github.com/dvankley/firefly-plaid-connector-2/issues/39#issuecomment-1817557063).

**Create New Credentials**
This is basically just going through the [Connecting Accounts](#connecting-bank-accounts-to-plaid) workflow again for that account, replacing the access token and account id in your configuration file, and restarting the connector. Unfortunately, as discussed in [https://github.com/dvankley/firefly-plaid-connector-2/issues/39](issue 39), this method permanently chews through your Item quota and is _NOT RECOMMENDED_ for that reason. At this point it does have the advantage of being simpler than update mode, which is why it's listed here as an option.

### Reporting Issues
If you have an issue, report it via the Github issue tracker. The odds of your issue being addressed will be increased if you include
 - A clear description of the desired outcome and current outcome
 - Relevant logs at the `TRACE` level with your issue report
 - A test case (i.e. in `net.djvk.fireflyPlaidConnector2.transactions.TransactionConverterTest`) to demonstrate your issue. If test infrastructure is missing to test the element of the code you see an issue with, let me know and I can work on improving that

To set the logging level see [configuration](#configuration)


# FAQ
#### Why did you make a new program rather than contributing to [firefly-plaid-connector](https://gitlab.com/GeorgeHahn/firefly-plaid-connector/)?
I initially tried firefly-plaid-connector, but I had a few issues with it, and it didn't fully support Plaid categories.
I tried to set it up for development locally, but after about an hour trying to get the right version of the .NET SDK to work,
I decided I was better off making my own gravy. So here we are.

#### Migrating Plaid from Development to Production
If you've been using the Plaid `development` environment and are now being compelled to move to `production`, here's a checklist of the steps (please submit an update to these if it changes):

1. Submit Plaid request for production access and wait for approval.
2. If you're in the US and want to use OAuth institutions, work through the list required for OAuth institutions. You can basically put "I'm just a hobbyist" for all the security etc. questions and they'll approve you.
3. Change your quickstart .env file to point to the production environment and update it with your new secret (your client id is probably the same).
4. Run the link flow through quickstart for all items you want to migrate, the same as you did initially for the development environment.
5. Stop the connector process if you're running it in polling mode somewhere.
6. Back up your production Firefly database.
7. Update your application config file.
   
   `plaid.url` should be https://production.plaid.com
   
   `plaid.secret` needs to be updated to your new secret.
   
   Each account should be updated with the new access token and plaid account id. Keep the same firefly account ids if you want to migrate in place.
8. If (like me) your connector's been down for a while, run the connector in batch mode to cover the time range from your last successful sync from Plaid.
9. Delete the cursor file in your persistence directory. Plaid couples the cursors values to their access tokens, so they don't cross over between environments.
10. Run the connector in polled mode per usual.
11. Take a close look at the time range when you switched from development to production and clean up any duplicates you find. The connector's update functionality won't properly bridge environments because the ids are different, so you may see some transactions around that time that should have been updated end up being duplicated. Just delete the old ones and hopefully you should be good going forward.


# Other Resources
#### Budget Notifications via Home Assistant
Do you, like me, run Home Assistant and also want real-ish time budget notifications? Then check out [this Node-RED](https://github.com/dvankley/homeassistant-firefly-budget-alert) flow.

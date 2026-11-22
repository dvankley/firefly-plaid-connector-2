<?php

declare(strict_types=1);

use FireflyIII\Console\Commands\Correction\CreatesGroupMemberships;
use FireflyIII\Repositories\User\UserRepositoryInterface;
use Illuminate\Contracts\Console\Kernel;
use Laravel\Passport\ClientRepository;

require 'vendor/autoload.php';
$app = require 'bootstrap/app.php';
$app->make(Kernel::class)->bootstrap();

$user = $app->make(UserRepositoryInterface::class)->store([
    'email' => 'connector-smoke@example.test',
    'role' => 'owner',
]);
CreatesGroupMemberships::createGroupMembership($user);
$app->make(ClientRepository::class)->createPersonalAccessGrantClient('Connector smoke test', 'users');

echo 'SMOKE_TOKEN=' . $user->createToken('Connector smoke test')->accessToken . PHP_EOL;

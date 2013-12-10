#!/bin/perl

# genMysqlGNomExAuditTrigDropCr8.pl
# 20120607 Modified from genMysqlAuditTabsGNomEx.pl
# Huntsman Cancer Institute, Research Informatics
# Generate SQL script for dropping and re-creating audit triggers for GNomEx MySQL databases.
# --------------------------------------------------------------------------------------------------
#  Modifications
# --------------------------------------------------------------------------------------------------


use strict;
use English '-no_match_vars';
use DBI;
use Time::Local;

if ( @ARGV != 5 ) { die ".\n. Usage: $0 <mysql_host> <user> <password> <target_db> <output-filename>\n.\n"; }

my $host  = $ARGV[0];
my $user  = $ARGV[1];
my $usrpw = $ARGV[2];
my $tgtDb = $ARGV[3];
my $outFl = $ARGV[4];

my $dbNm = 'information_schema';
my $tLoopCnt = 0;
my $cLoopCnt = 0;
my ( $tabName, $colName, $trigName, $colDTyp, $colOrd, $tQry, $cQry, $tCur, $cCur, $tAIbuf, $tAUbuf, $tADbuf, $tAIvalBuf, $tAUvalBuf, $tADvalBuf );

my $start = localtime time;
my $endTm;

my $tDbh = DBI->connect( "DBI:mysql:$dbNm;host=$host", $user, $usrpw, { RaiseError => 1 } ) || die "Could not connect to database: $DBI::errstr";
my $cDbh = DBI->connect( "DBI:mysql:$dbNm;host=$host", $user, $usrpw, { RaiseError => 1 } ) || die "Could not connect to database: $DBI::errstr";

if (-e $outFl) { die ".\n. Output file $outFl already exists!\n.\n"; }

open( MYOUTFILE, ">$outFl" ) || die ".\n. Unable to open output file $outFl for writing.\n.\n";

print MYOUTFILE "\nUSE gnomex\;\n\n";
print MYOUTFILE "delimiter \$\$\n\n";

print MYOUTFILE "DROP PROCEDURE IF EXISTS setAppUser\$\$\n";
print MYOUTFILE "CREATE PROCEDURE setAppUser( IN userName text)\n";
print MYOUTFILE "BEGIN\n";
print MYOUTFILE "  SET \@userName=userName;\n";
print MYOUTFILE "END;\n";
print MYOUTFILE "\$\$\n\n\n";

$tQry = "SELECT t.table_name ".
          "FROM information_schema.tables t ".
         "WHERE t.table_schema = '$tgtDb' ".
           "AND t.table_name NOT IN ('dtproperties','TransferLog') ".
           "AND t.table_name NOT LIKE '%\_Audit' ESCAPE '\' ".
         "ORDER BY t.table_name";

$tCur = $tDbh->prepare( $tQry );
$tCur->execute();
$tCur->bind_columns( undef, \$trigName );

while ( $tCur->fetch() ) {
  $tLoopCnt++;
  print MYOUTFILE "DROP TRIGGER IF EXISTS TrAI_".$trigName."_FER\n\$\$\n";
  print MYOUTFILE "DROP TRIGGER IF EXISTS TrAU_".$trigName."_FER\n\$\$\n";
  print MYOUTFILE "DROP TRIGGER IF EXISTS TrAD_".$trigName."_FER\n\$\$\n";
}
$tCur->finish();

print  "\n\n";
print  "--------------------------------------------------------------------------------------------------\n";
printf ". Trigger Loops:  %5d \n", $tLoopCnt;
print  "\n\n";

# Reset for next loop.
$tLoopCnt = 0;

$tQry = "SELECT replace(replace(t.table_name, '_Audit', ''), '_audit', '') ".
          "FROM information_schema.tables t ".
         "WHERE t.table_schema = '$tgtDb' ".
           "AND t.table_name LIKE '%\_Audit' ESCAPE '\' ".
         "ORDER BY t.table_name";

# print "\n.\n. DEBUG Query: \n\n$tQry \n.\n.\n\n\n";

$tCur = $tDbh->prepare( $tQry );
$tCur->execute();
$tCur->bind_columns( undef, \$tabName );
#$tCur->fetch() || die "  Unable to get table names.  Err: $DBI::err, ErrStr: $DBI::errstr, State: $DBI::state";


while ( $tCur->fetch() ) {
  $tLoopCnt++;
  printf "Table %3d : %-31s \n", $tLoopCnt, $tabName;

  #
  # Start CREATE TABLE command.
  #
  print MYOUTFILE "\n\n--\n";
  print MYOUTFILE "-- Audit Table For $tabName \n";
  print MYOUTFILE "--\n";
  print MYOUTFILE "\nCREATE TABLE IF NOT EXISTS \`".$tabName."_Audit\` (";
  print MYOUTFILE "\n  \`AuditAppuser\`       varchar(128) NOT NULL";
  print MYOUTFILE "\n ,\`AuditOperation\`     char(1)      NOT NULL";
  print MYOUTFILE "\n ,\`AuditSystemUser\`    varchar(30)  NOT NULL";
  print MYOUTFILE "\n ,\`AuditOperationDate\` datetime     NOT NULL";

  #
  # Prepare string buffers for CREATE TRIGGER commands.
  #
  $tAIbuf = "CREATE TRIGGER TrAI_".$tabName."_FER AFTER INSERT ON $tabName FOR EACH ROW".
            "\nBEGIN".
            "\n  INSERT INTO ".$tabName."_Audit".
            "\n  ( AuditAppuser".
            "\n  , AuditOperation".
            "\n  , AuditSystemUser".
            "\n  , AuditOperationDate";
  $tAUbuf = "CREATE TRIGGER TrAU_".$tabName."_FER AFTER UPDATE ON $tabName FOR EACH ROW".
            "\nBEGIN".
            "\n  INSERT INTO ".$tabName."_Audit".
            "\n  ( AuditAppuser".
            "\n  , AuditOperation".
            "\n  , AuditSystemUser".
            "\n  , AuditOperationDate";
  $tADbuf = "CREATE TRIGGER TrAD_".$tabName."_FER AFTER DELETE ON $tabName FOR EACH ROW".
            "\nBEGIN".
            "\n  INSERT INTO ".$tabName."_Audit".
            "\n  ( AuditAppuser".
            "\n  , AuditOperation".
            "\n  , AuditSystemUser".
            "\n  , AuditOperationDate";

  $tAIvalBuf = "\n  VALUES".
               "\n  ( CASE WHEN \@userName IS NULL THEN 'No Context' else \@userName end".
               "\n  , 'I'".
               "\n  , USER()".
               "\n  , NOW()";
  $tAUvalBuf = "\n  VALUES".
               "\n  ( CASE WHEN \@userName IS NULL THEN 'No Context' else \@userName end".
               "\n  , 'U'".
               "\n  , USER()".
               "\n  , NOW()";
  $tADvalBuf = "\n  VALUES".
               "\n  ( CASE WHEN \@userName IS NULL THEN 'No Context' else \@userName end".
               "\n  , 'D'".
               "\n  , USER()".
               "\n  , NOW()";

  #
  # Prepare column cursor for current table.
  #
  $cQry = "SELECT c.column_name, c.column_type, c.ordinal_position ".
            "FROM information_schema.columns c ".
           "WHERE c.table_schema = '$tgtDb' ".
             "AND c.table_name = '$tabName' ".
           "ORDER BY c.ordinal_position";

  $cCur = $cDbh->prepare( $cQry );
  $cCur->execute();
  $cCur->bind_columns( undef, \$colName, \$colDTyp, \$colOrd );
  #$cCur->fetch() || die "  Unable to get table names.  Err: $DBI::err, ErrStr: $DBI::errstr, State: $DBI::state";

  while ( $cCur->fetch() ) {
    $cLoopCnt++;

    #
    # Column for table create
    #
    print MYOUTFILE "\n ,\`$colName\`  $colDTyp  NULL DEFAULT NULL";

    #
    # Add columns to CREATE TRIGGER commands.
    #
    $tAIbuf = $tAIbuf."\n  , $colName";
    $tAUbuf = $tAUbuf."\n  , $colName";
    $tADbuf = $tADbuf."\n  , $colName";

    $tAIvalBuf = $tAIvalBuf."\n  , NEW\.$colName";
    $tAUvalBuf = $tAUvalBuf."\n  , NEW\.$colName";
    $tADvalBuf = $tADvalBuf."\n  , OLD\.$colName";
  }
  $cCur->finish();

  #
  # Finish CREATE TABLE command.
  #
  print MYOUTFILE "\n) ENGINE=InnoDB\n";
  print MYOUTFILE "\$\$\n";

  #
  # Append CREATE TRIGGER commands.
  #
  print MYOUTFILE "\n\n--\n";
  print MYOUTFILE "-- Audit Triggers For $tabName \n";
  print MYOUTFILE "--\n";
  print MYOUTFILE "\n\n$tAIbuf \)$tAIvalBuf \)\;\nEND\;\n\$\$\n";
  print MYOUTFILE "\n\n$tAUbuf \)$tAUvalBuf \)\;\nEND\;\n\$\$\n";
  print MYOUTFILE "\n\n$tADbuf \)$tADvalBuf \)\;\nEND\;\n\$\$\n";
}
$tCur->finish();

close( MYOUTFILE );

$endTm = localtime time;

print  "\n\n";
print  "--------------------------------------------------------------------------------------------------\n";
printf ". Table  Loops:  %5d \n", $tLoopCnt;
printf ". Column Loops:  %5d \n", $cLoopCnt;
print  ".\n. Start Time :   $start           End Time :  $endTm \n.\n";
print  "--------------------------------------------------------------------------------------------------\n";
print  "\n\n";

exit 0;

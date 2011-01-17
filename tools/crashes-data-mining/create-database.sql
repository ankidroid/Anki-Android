drop database crashes_data_mining;
create database crashes_data_mining;
use crashes_data_mining;

CREATE TABLE `crashes` (
 `id` INT,
  `title` VARCHAR(450) NULL ,
  `year` TINYINT NULL ,
  `month` TINYINT NULL ,
  `day` TINYINT NULL ,
  `timezone` VARCHAR(450) NULL ,
  `version` VARCHAR(450) NULL ,
  `android` VARCHAR(450) NULL ,
  `brand` VARCHAR(450) NULL ,
  `model` VARCHAR(450) NULL ,
  `product` VARCHAR(450) NULL ,
  `device` VARCHAR(450) NULL ,
  `memory` BIGINT NULL ,
  `associated_bug` INT NULL ,
  `occurrences_of_associated_bug` INT NULL ,
  `time` VARCHAR(450) NULL)
ENGINE = InnoDB;

#!/usr/bin/env ruby

# frozen_string_literal: true

$LOAD_PATH.unshift File.expand_path(File.join(File.dirname(__FILE__), '../lib'))

require 'deltafi'
require 'pry'

x = binding
x.pry
